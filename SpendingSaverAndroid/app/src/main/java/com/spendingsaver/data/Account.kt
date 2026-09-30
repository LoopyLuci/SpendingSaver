package com.spendingsaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // "checking", "savings", etc.
    val balance: Double,
    val currency: String = "USD",
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    override val dirty: Boolean = false,
    override val remoteId: String? = null
) : SyncEntity
