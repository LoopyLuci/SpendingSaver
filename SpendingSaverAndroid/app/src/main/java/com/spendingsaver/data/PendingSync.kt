package com.spendingsaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Outbox for offline changes. Every local insert/update/delete writes a row
 * here so the [com.spendingsaver.sync.SyncWorker] can replay pending changes
 * against the backend when connectivity returns.
 */
@Entity(tableName = "pending_sync")
data class PendingSync(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entityType: String,   // "account" | "category" | "transaction" | "budget"
    val operation: String,    // "insert" | "update" | "delete"
    val remoteId: String? = null,
    val localData: String,    // JSON of the row
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
