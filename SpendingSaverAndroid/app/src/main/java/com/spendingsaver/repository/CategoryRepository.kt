package com.spendingsaver.repository

import com.spendingsaver.data.Category
import com.spendingsaver.data.CategoryDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val syncQueue: SyncQueue
) {
    val categories: Flow<List<Category>> = categoryDao.getAll()

    suspend fun addCategory(category: Category): Category {
        val dirty = category.copy(dirty = true, updatedAt = System.currentTimeMillis())
        categoryDao.insert(dirty)
        syncQueue.enqueue("category", "insert", dirty)
        return dirty
    }

    suspend fun updateCategory(category: Category) {
        val dirty = category.copy(dirty = true, updatedAt = System.currentTimeMillis())
        categoryDao.update(dirty)
        syncQueue.enqueue("category", "update", dirty)
    }

    /** Soft delete so the removal is captured and synced. */
    suspend fun deleteCategory(category: Category) {
        val soft = category.copy(deleted = true, dirty = true, updatedAt = System.currentTimeMillis())
        categoryDao.update(soft)
        syncQueue.enqueue("category", "delete", soft)
    }
}
