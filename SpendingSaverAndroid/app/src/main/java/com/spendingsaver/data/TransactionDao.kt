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

    /** Insert-or-replace by primary key (used by sync and restore). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Transaction)

    /** Every row, including soft-deleted tombstones, changed at or after [sinceMs]. */
    @Query("SELECT * FROM transactions WHERE updatedAt >= :sinceMs ORDER BY updatedAt")
    suspend fun getModifiedSince(sinceMs: Long): List<Transaction>

    /** Every row including tombstones; used to plan a restore. */
    @Query("SELECT * FROM transactions")
    suspend fun getAllIncludingDeleted(): List<Transaction>
}
