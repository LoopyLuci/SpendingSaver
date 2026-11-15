package com.spendingsaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val color: String? = null,
    /** Soft-delete flag so the user can undo; rows are never physically removed. */
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
