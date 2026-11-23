package com.spendingsaver.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY name")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE deleted = 0")
    suspend fun getAllOnce(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): Category?

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    /** Insert-or-replace by primary key (used by sync and restore). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Category)

    /** Every row, including soft-deleted tombstones, changed at or after [sinceMs]. */
    @Query("SELECT * FROM categories WHERE updatedAt >= :sinceMs ORDER BY updatedAt")
    suspend fun getModifiedSince(sinceMs: Long): List<Category>

    /** Every row including tombstones; used to plan a restore. */
    @Query("SELECT * FROM categories")
    suspend fun getAllIncludingDeleted(): List<Category>
}
