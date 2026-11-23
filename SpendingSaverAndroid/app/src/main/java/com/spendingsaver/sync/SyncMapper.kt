package com.spendingsaver.sync

import com.spendingsaver.data.Account
import com.spendingsaver.data.Budget
import com.spendingsaver.data.Category
import com.spendingsaver.data.Transaction
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Converts between local Room entities and sync wire records.
 *
 * Field coverage is deliberately limited to what both apps store. In particular
 * `Category.parentId` is Android-only; the hub has no such column, so remote
 * updates never overwrite it (see [categoryFromRecord]).
 */
object SyncMapper {

    fun toIso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    /** Parses an ISO-8601 instant to epoch ms, or null if it is not a valid instant. */
    fun fromIso(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                // Offset form such as 2026-01-01T10:00:00+00:00
                java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    // ---- local -> wire ----
    fun toRecord(a: Account) = SyncRecord(
        SyncEntity.ACCOUNT, a.id, toIso(a.updatedAt), a.deleted,
        mapOf("name" to a.name, "type" to a.type, "balance" to a.balance, "currency" to a.currency),
    )

    fun toRecord(c: Category) = SyncRecord(
        SyncEntity.CATEGORY, c.id, toIso(c.updatedAt), c.deleted,
        mapOf("name" to c.name, "color" to c.color),
    )

    fun toRecord(t: Transaction) = SyncRecord(
        SyncEntity.TRANSACTION, t.id, toIso(t.updatedAt), t.deleted,
        mapOf(
            "account_id" to t.accountId, "amount" to t.amount, "description" to t.description,
            "category_id" to t.categoryId, "date" to t.date, "notes" to t.notes,
            "created_at" to toIso(t.createdAt),
        ),
    )

    fun toRecord(b: Budget) = SyncRecord(
        SyncEntity.BUDGET, b.id, toIso(b.updatedAt), b.deleted,
        mapOf(
            "category_id" to b.categoryId, "amount" to b.amount, "period" to b.period,
            "start_date" to b.startDate,
        ),
    )

    // ---- wire -> local (null when the record is malformed and must be skipped) ----
    private fun Map<String, Any?>.str(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.num(key: String): Double? = (this[key] as? Number)?.toDouble()

    fun accountFromRecord(r: SyncRecord): Account? {
        val ms = fromIso(r.updatedAt) ?: return null
        return Account(
            id = r.id,
            name = r.data.str("name") ?: return null,
            type = r.data.str("type") ?: return null,
            balance = r.data.num("balance") ?: return null,
            currency = r.data.str("currency") ?: return null,
            deleted = r.deleted,
            updatedAt = ms,
        )
    }

    /** [existing] supplies the Android-only `parentId`, which the hub does not carry. */
    fun categoryFromRecord(r: SyncRecord, existing: Category?): Category? {
        val ms = fromIso(r.updatedAt) ?: return null
        return Category(
            id = r.id,
            name = r.data.str("name") ?: return null,
            parentId = existing?.parentId,
            color = r.data.str("color"),
            deleted = r.deleted,
            updatedAt = ms,
        )
    }

    fun transactionFromRecord(r: SyncRecord, existing: Transaction?): Transaction? {
        val ms = fromIso(r.updatedAt) ?: return null
        return Transaction(
            id = r.id,
            accountId = r.data.str("account_id") ?: return null,
            amount = r.data.num("amount") ?: return null,
            description = r.data.str("description") ?: return null,
            categoryId = r.data.str("category_id"),
            date = r.data.str("date") ?: return null,
            notes = r.data.str("notes"),
            createdAt = fromIso(r.data.str("created_at")) ?: existing?.createdAt ?: ms,
            deleted = r.deleted,
            updatedAt = ms,
        )
    }

    fun budgetFromRecord(r: SyncRecord): Budget? {
        val ms = fromIso(r.updatedAt) ?: return null
        return Budget(
            id = r.id,
            categoryId = r.data.str("category_id") ?: return null,
            amount = r.data.num("amount") ?: return null,
            period = r.data.str("period") ?: return null,
            startDate = r.data.str("start_date") ?: "",
            deleted = r.deleted,
            updatedAt = ms,
        )
    }
}
