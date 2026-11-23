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

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId AND amount < 0 AND deleted = 0")
    suspend fun getExpensesByCategory(categoryId: String): List<Transaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
