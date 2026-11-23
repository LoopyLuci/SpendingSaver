package com.spendingsaver.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.spendingsaver.data.Account
import com.spendingsaver.data.AppDatabase
import com.spendingsaver.data.Budget
import com.spendingsaver.data.Category
import com.spendingsaver.data.Transaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-contained local backup & restore. Serialises the entire local dataset
 * (accounts, categories, transactions, budgets) to a single JSON document so
 * the app needs no companion desktop server to move data between devices.
 *
 * Restore runs inside a single Room transaction so the local DB is never left
 * half-written if the import is interrupted or malformed.
 */
@Singleton
class DataBackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val gson: Gson,
) {
    data class BackupDocument(
        val version: Int = 1,
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

    /** Atomically replace the local dataset from a backup JSON document. */
    suspend fun restore(json: String) {
        val doc = gson.fromJson(json, BackupDocument::class.java)
        db.withTransaction {
            db.accountDao().deleteAll()
            db.categoryDao().deleteAll()
            db.transactionDao().deleteAll()
            db.budgetDao().deleteAll()
            doc.accounts.forEach { db.accountDao().insert(it) }
            doc.categories.forEach { db.categoryDao().insert(it) }
            doc.transactions.forEach { db.transactionDao().insert(it) }
            doc.budgets.forEach { db.budgetDao().insert(it) }
        }
    }
}
