package com.spendingsaver.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE deleted = 0")
    fun getAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): Budget?

    @Insert
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
    @Query("SELECT * FROM budgets WHERE deleted = 0")
    suspend fun getAllOnce(): List<Budget>

    /** Insert-or-replace by primary key (used by sync and restore). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Budget)

    /** Every row, including soft-deleted tombstones, changed at or after [sinceMs]. */
    @Query("SELECT * FROM budgets WHERE updatedAt >= :sinceMs ORDER BY updatedAt")
    suspend fun getModifiedSince(sinceMs: Long): List<Budget>

    /** Every row including tombstones; used to plan a restore. */
    @Query("SELECT * FROM budgets")
    suspend fun getAllIncludingDeleted(): List<Budget>
}
