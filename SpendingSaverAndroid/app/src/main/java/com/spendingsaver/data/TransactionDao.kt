package com.spendingsaver.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE deleted = 0")
    suspend fun getAllOnce(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): Transaction?

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Upsert
    suspend fun upsert(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId AND amount < 0 AND deleted = 0")
    suspend fun getExpensesByCategory(categoryId: String): List<Transaction>

    @Query("UPDATE transactions SET deleted = :deleted, dirty = :dirty, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSyncFlags(id: String, deleted: Boolean, dirty: Boolean, updatedAt: Long)
}
