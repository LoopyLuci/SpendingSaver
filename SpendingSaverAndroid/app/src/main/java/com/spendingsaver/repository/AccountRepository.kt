package com.spendingsaver.repository

import com.spendingsaver.data.Account
import com.spendingsaver.data.AccountDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val syncQueue: SyncQueue
) {
    val accounts: Flow<List<Account>> = accountDao.getAll()

    /** Insert a new account, mark it dirty and queue it for upload. */
    suspend fun addAccount(account: Account): Account {
        val dirty = account.copy(dirty = true, updatedAt = System.currentTimeMillis())
        accountDao.insert(dirty)
        syncQueue.enqueue("account", "insert", dirty)
        return dirty
    }

    suspend fun updateAccount(account: Account) {
        val dirty = account.copy(dirty = true, updatedAt = System.currentTimeMillis())
        accountDao.update(dirty)
        syncQueue.enqueue("account", "update", dirty)
    }

    /** Soft delete: mark the row deleted + dirty so it syncs, never remove it. */
    suspend fun deleteAccount(account: Account) {
        val soft = account.copy(deleted = true, dirty = true, updatedAt = System.currentTimeMillis())
        accountDao.update(soft)
        syncQueue.enqueue("account", "delete", soft)
    }
}
