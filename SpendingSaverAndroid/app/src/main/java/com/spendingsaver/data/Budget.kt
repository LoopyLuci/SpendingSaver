package com.spendingsaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val amount: Double,
    val period: String, // "weekly" | "monthly" | "yearly"
    val startDate: String = "",
    /** Soft-delete flag so the user can undo; rows are never physically removed. */
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
