package com.spendingsaver.sync

import androidx.room.withTransaction
import com.spendingsaver.data.AppDatabase

/** Room-backed [SyncStore]. Applies hub changes in one transaction with last-write-wins. */
class RoomSyncStore(private val db: AppDatabase) : SyncStore {

    override suspend fun modifiedSince(sinceMs: Long): List<SyncRecord> = db.withTransaction {
        db.accountDao().getModifiedSince(sinceMs).map(SyncMapper::toRecord) +
            db.categoryDao().getModifiedSince(sinceMs).map(SyncMapper::toRecord) +
            db.transactionDao().getModifiedSince(sinceMs).map(SyncMapper::toRecord) +
            db.budgetDao().getModifiedSince(sinceMs).map(SyncMapper::toRecord)
    }

    override suspend fun apply(records: List<SyncRecord>): Int {
        if (records.isEmpty()) return 0
        var changed = 0
        db.withTransaction {
            for (rec in records) {
                val incomingMs = SyncMapper.fromIso(rec.updatedAt) ?: continue
                when (rec.entity) {
                    SyncEntity.ACCOUNT -> {
                        val local = db.accountDao().getById(rec.id)
                        if (local != null && local.updatedAt >= incomingMs) continue
                        SyncMapper.accountFromRecord(rec)?.let { db.accountDao().upsert(it); changed++ }
                    }
                    SyncEntity.CATEGORY -> {
                        val local = db.categoryDao().getById(rec.id)
                        if (local != null && local.updatedAt >= incomingMs) continue
                        SyncMapper.categoryFromRecord(rec, local)?.let { db.categoryDao().upsert(it); changed++ }
                    }
                    SyncEntity.TRANSACTION -> {
                        val local = db.transactionDao().getById(rec.id)
                        if (local != null && local.updatedAt >= incomingMs) continue
                        SyncMapper.transactionFromRecord(rec, local)?.let { db.transactionDao().upsert(it); changed++ }
                    }
                    SyncEntity.BUDGET -> {
                        val local = db.budgetDao().getById(rec.id)
                        if (local != null && local.updatedAt >= incomingMs) continue
                        SyncMapper.budgetFromRecord(rec)?.let { db.budgetDao().upsert(it); changed++ }
                    }
                }
            }
        }
        return changed
    }
}
