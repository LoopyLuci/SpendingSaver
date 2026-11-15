package com.spendingsaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // "checking", "savings", etc.
    val balance: Double,
    val currency: String = "USD",
    /** Soft-delete flag so the user can undo; rows are never physically removed. */
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
