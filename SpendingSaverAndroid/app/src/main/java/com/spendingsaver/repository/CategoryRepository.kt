package com.spendingsaver.repository

import com.spendingsaver.data.Category
import com.spendingsaver.data.CategoryDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) {
    val categories: Flow<List<Category>> = categoryDao.getAll()

    suspend fun addCategory(category: Category): Category {
        categoryDao.insert(category.copy(updatedAt = System.currentTimeMillis()))
        return category
    }

    suspend fun updateCategory(category: Category) {
        categoryDao.update(category.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Soft delete: mark the row deleted; it is never physically removed. */
    suspend fun deleteCategory(category: Category) {
        categoryDao.update(category.copy(deleted = true, updatedAt = System.currentTimeMillis()))
    }
}
