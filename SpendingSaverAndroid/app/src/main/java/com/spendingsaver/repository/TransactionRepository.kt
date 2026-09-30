package com.spendingsaver.repository

import com.spendingsaver.data.Transaction
import com.spendingsaver.data.TransactionDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val syncQueue: SyncQueue
) {
    val transactions: Flow<List<Transaction>> = transactionDao.getAll()

    suspend fun addTransaction(transaction: Transaction): Transaction {
        val dirty = transaction.copy(dirty = true, updatedAt = System.currentTimeMillis())
        transactionDao.insert(dirty)
        syncQueue.enqueue("transaction", "insert", dirty)
        return dirty
    }

    suspend fun updateTransaction(transaction: Transaction) {
        val dirty = transaction.copy(dirty = true, updatedAt = System.currentTimeMillis())
        transactionDao.update(dirty)
        syncQueue.enqueue("transaction", "update", dirty)
    }

    /** Soft delete so the removal is captured and synced. */
    suspend fun deleteTransaction(transaction: Transaction) {
        val soft = transaction.copy(deleted = true, dirty = true, updatedAt = System.currentTimeMillis())
        transactionDao.update(soft)
        syncQueue.enqueue("transaction", "delete", soft)
    }
}
