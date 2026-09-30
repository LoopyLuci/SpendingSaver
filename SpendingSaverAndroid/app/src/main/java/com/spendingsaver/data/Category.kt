package com.spendingsaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val color: String? = null,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    override val dirty: Boolean = false,
    override val remoteId: String? = null
) : SyncEntity
