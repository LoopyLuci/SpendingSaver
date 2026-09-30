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

    @Upsert
    suspend fun upsert(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("UPDATE accounts SET deleted = :deleted, dirty = :dirty, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSyncFlags(id: String, deleted: Boolean, dirty: Boolean, updatedAt: Long)
}
