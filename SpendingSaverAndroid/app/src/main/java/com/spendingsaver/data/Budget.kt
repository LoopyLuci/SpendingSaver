package com.spendingsaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val amount: Double,
    val period: String,          // "monthly" or "yearly"
    val startDate: String,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    override val dirty: Boolean = false,
    override val remoteId: String? = null
) : SyncEntity
