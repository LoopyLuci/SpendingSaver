package com.spendingsaver.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY name")
    fun getAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE deleted = 0")
    suspend fun getAllOnce(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): Account?

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    /** Insert-or-replace by primary key (used by sync and restore). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Account)

    /** Every row, including soft-deleted tombstones, changed at or after [sinceMs]. */
    @Query("SELECT * FROM accounts WHERE updatedAt >= :sinceMs ORDER BY updatedAt")
    suspend fun getModifiedSince(sinceMs: Long): List<Account>

    /** Every row including tombstones; used to plan a restore. */
    @Query("SELECT * FROM accounts")
    suspend fun getAllIncludingDeleted(): List<Account>
}
