package com.spendingsaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val amount: Double,          // positive income, negative expense
    val description: String,
    val categoryId: String? = null,
    val date: String,            // YYYY-MM-DD
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Soft-delete flag so the user can undo; rows are never physically removed. */
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
