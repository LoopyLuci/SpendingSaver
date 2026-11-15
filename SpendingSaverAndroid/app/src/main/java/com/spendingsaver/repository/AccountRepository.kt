package com.spendingsaver.repository

import com.spendingsaver.data.Account
import com.spendingsaver.data.AccountDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
) {
    val accounts: Flow<List<Account>> = accountDao.getAll()

    suspend fun addAccount(account: Account): Account {
        accountDao.insert(account.copy(updatedAt = System.currentTimeMillis()))
        return account
    }

    suspend fun updateAccount(account: Account) {
        accountDao.update(account.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Soft delete: mark the row deleted; it is never physically removed. */
    suspend fun deleteAccount(account: Account) {
        accountDao.update(account.copy(deleted = true, updatedAt = System.currentTimeMillis()))
    }
}
