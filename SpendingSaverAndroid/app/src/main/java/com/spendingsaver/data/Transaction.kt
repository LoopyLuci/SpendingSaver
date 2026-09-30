package com.spendingsaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val amount: Double,          // positive income, negative expense
    val description: String,
    val categoryId: String? = null,
    val date: String,            // YYYY-MM-DD
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    override val dirty: Boolean = false,
    override val remoteId: String? = null
) : SyncEntity
