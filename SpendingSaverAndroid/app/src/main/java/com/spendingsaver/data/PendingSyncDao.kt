package com.spendingsaver.data

import androidx.room.*

@Dao
interface PendingSyncDao {
    @Insert
    suspend fun insert(pending: PendingSync)

    @Query("SELECT * FROM pending_sync WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsynced(): List<PendingSync>

    @Query("SELECT * FROM pending_sync WHERE synced = 1 ORDER BY createdAt DESC")
    suspend fun getSynced(): List<PendingSync>

    @Query("UPDATE pending_sync SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM pending_sync WHERE synced = 1")
    suspend fun purgeSynced()
}
