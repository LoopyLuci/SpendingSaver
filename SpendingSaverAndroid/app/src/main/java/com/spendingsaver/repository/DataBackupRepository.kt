package com.spendingsaver.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.spendingsaver.data.Account
import com.spendingsaver.data.AppDatabase
import com.spendingsaver.data.Budget
import com.spendingsaver.data.Category
import com.spendingsaver.data.Transaction
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a backup file is not a complete, supported SpendingSaver backup. */
class InvalidBackupException(message: String) : Exception(message)

/**
 * Self-contained local backup & restore. Serialises the local dataset
 * (accounts, categories, transactions, budgets) to a single JSON document so
 * the app needs no companion server. This is a user-initiated file export; it
 * is separate from the optional desktop sync hub (see `com.spendingsaver.sync`).
 *
 * Restore replaces the *active* dataset with the backup's contents inside one
 * Room transaction. Rows that are not in the backup are soft-deleted (kept as
 * tombstones) rather than physically removed, so that — if sync is enabled —
 * the replacement propagates to the hub instead of the removed rows reappearing.
 * An invalid file is rejected before anything is changed.
 */
@Singleton
class DataBackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val gson: Gson,
) {
    data class BackupDocument(
        val version: Int = BackupCodec.CURRENT_VERSION,
        val exportedAt: Long = System.currentTimeMillis(),
        val accounts: List<Account> = emptyList(),
        val categories: List<Category> = emptyList(),
        val transactions: List<Transaction> = emptyList(),
        val budgets: List<Budget> = emptyList(),
    )

    /** Snapshot all active (non-deleted) rows into a JSON string. */
    suspend fun export(): String = db.withTransaction {
        val doc = BackupDocument(
            accounts = db.accountDao().getAllOnce(),
            categories = db.categoryDao().getAllOnce(),
            transactions = db.transactionDao().getAllOnce(),
            budgets = db.budgetDao().getAllOnce(),
        )
        gson.toJson(doc)
    }

    /** Atomically replace the active dataset from a backup JSON document. */
    suspend fun restore(json: String) {
        val doc = BackupCodec.parse(gson, json) // validates fully before touching the database
        val now = System.currentTimeMillis()
        db.withTransaction {
            val accounts = BackupPlanner.plan(
                db.accountDao().getAllIncludingDeleted(), doc.accounts,
                id = { it.id }, isDeleted = { it.deleted },
                tombstone = { it.copy(deleted = true, updatedAt = now) },
                restamp = { it.copy(updatedAt = now) },
            )
            val categories = BackupPlanner.plan(
                db.categoryDao().getAllIncludingDeleted(), doc.categories,
                id = { it.id }, isDeleted = { it.deleted },
                tombstone = { it.copy(deleted = true, updatedAt = now) },
                restamp = { it.copy(updatedAt = now) },
            )
            val transactions = BackupPlanner.plan(
                db.transactionDao().getAllIncludingDeleted(), doc.transactions,
                id = { it.id }, isDeleted = { it.deleted },
                tombstone = { it.copy(deleted = true, updatedAt = now) },
                restamp = { it.copy(updatedAt = now) },
            )
            val budgets = BackupPlanner.plan(
                db.budgetDao().getAllIncludingDeleted(), doc.budgets,
                id = { it.id }, isDeleted = { it.deleted },
                tombstone = { it.copy(deleted = true, updatedAt = now) },
                restamp = { it.copy(updatedAt = now) },
            )
            accounts.forEach { db.accountDao().upsert(it) }
            categories.forEach { db.categoryDao().upsert(it) }
            transactions.forEach { db.transactionDao().upsert(it) }
            budgets.forEach { db.budgetDao().upsert(it) }
        }
    }
}

/** Parsing and validation of backup files. Pure, so it is unit-tested without Android. */
object BackupCodec {
    const val CURRENT_VERSION = 1

    @Suppress("SENSELESS_COMPARISON", "USELESS_CAST")
    fun parse(gson: Gson, json: String): DataBackupRepository.BackupDocument {
        val root = try {
            JsonParser.parseString(json)
        } catch (e: JsonParseException) {
            throw InvalidBackupException("The file is not valid JSON")
        }
        if (!root.isJsonObject) throw InvalidBackupException("The file is not a SpendingSaver backup")
        val obj: JsonObject = root.asJsonObject

        val version = obj.get("version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
            ?: throw InvalidBackupException("The file is not a SpendingSaver backup (no version)")
        if (version != CURRENT_VERSION) {
            throw InvalidBackupException("Unsupported backup version $version (this app reads version $CURRENT_VERSION)")
        }
        for (key in listOf("accounts", "categories", "transactions", "budgets")) {
            if (obj.get(key)?.isJsonArray != true) {
                throw InvalidBackupException("The backup is incomplete: missing \"$key\"")
            }
        }

        val doc = try {
            gson.fromJson(obj, DataBackupRepository.BackupDocument::class.java)
        } catch (e: JsonParseException) {
            throw InvalidBackupException("The backup contains malformed data")
        } ?: throw InvalidBackupException("The backup is empty")

        // Gson bypasses Kotlin null-safety, so check the required fields explicitly.
        doc.accounts.forEach {
            if ((it.id as String?).isNullOrBlank() || (it.name as String?) == null ||
                (it.type as String?) == null || (it.currency as String?) == null
            ) throw InvalidBackupException("The backup contains an invalid account")
        }
        doc.categories.forEach {
            if ((it.id as String?).isNullOrBlank() || (it.name as String?) == null) {
                throw InvalidBackupException("The backup contains an invalid category")
            }
        }
        doc.transactions.forEach {
            if ((it.id as String?).isNullOrBlank() || (it.accountId as String?) == null ||
                (it.description as String?) == null || (it.date as String?) == null
            ) throw InvalidBackupException("The backup contains an invalid transaction")
        }
        doc.budgets.forEach {
            if ((it.id as String?).isNullOrBlank() || (it.categoryId as String?) == null ||
                (it.period as String?) == null || (it.startDate as String?) == null
            ) throw InvalidBackupException("The backup contains an invalid budget")
        }
        val allIds = listOf(doc.accounts.map { it.id }, doc.categories.map { it.id },
            doc.transactions.map { it.id }, doc.budgets.map { it.id })
        if (allIds.any { ids -> ids.size != ids.toSet().size }) {
            throw InvalidBackupException("The backup contains duplicate ids")
        }
        return doc
    }
}

/** Computes the rows a restore must write. Pure, so it is unit-tested without Room. */
object BackupPlanner {
    /**
     * Returns the rows to upsert: every restored row (re-stamped) plus a tombstone
     * for each currently active row that the backup does not contain. Rows that
     * are already soft-deleted and absent from the backup are left alone.
     */
    fun <T> plan(
        existing: List<T>,
        restored: List<T>,
        id: (T) -> String,
        isDeleted: (T) -> Boolean,
        tombstone: (T) -> T,
        restamp: (T) -> T,
    ): List<T> {
        val keep = restored.map(id).toSet()
        val tombstones = existing.filter { id(it) !in keep && !isDeleted(it) }.map(tombstone)
        return restored.map(restamp) + tombstones
    }
}
