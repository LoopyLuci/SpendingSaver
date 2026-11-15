package com.spendingsaver.repository

import com.spendingsaver.data.Transaction
import com.spendingsaver.data.TransactionDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) {
    val transactions: Flow<List<Transaction>> = transactionDao.getAll()

    suspend fun addTransaction(transaction: Transaction): Transaction {
        transactionDao.insert(transaction.copy(updatedAt = System.currentTimeMillis()))
        return transaction
    }

    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.update(transaction.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Soft delete: mark the row deleted; it is never physically removed. */
    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.update(transaction.copy(deleted = true, updatedAt = System.currentTimeMillis()))
    }
}
