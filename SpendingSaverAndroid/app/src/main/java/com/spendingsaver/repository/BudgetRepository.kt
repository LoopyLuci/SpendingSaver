package com.spendingsaver.repository

import com.spendingsaver.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetDao: BudgetDao,
    private val syncQueue: SyncQueue
) {
    val accounts: Flow<List<Account>> = accountRepository.accounts
    val categories: Flow<List<Category>> = categoryRepository.categories
    val transactions: Flow<List<Transaction>> = transactionRepository.transactions
    val budgets: Flow<List<Budget>> = budgetDao.getAll()

    suspend fun addAccount(account: Account) = accountRepository.addAccount(account)
    suspend fun addCategory(category: Category) = categoryRepository.addCategory(category)
    suspend fun addTransaction(transaction: Transaction) = transactionRepository.addTransaction(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionRepository.updateTransaction(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionRepository.deleteTransaction(transaction)
    suspend fun deleteAccount(account: Account) = accountRepository.deleteAccount(account)
    suspend fun deleteCategory(category: Category) = categoryRepository.deleteCategory(category)

    suspend fun addBudget(budget: Budget): Budget {
        val dirty = budget.copy(dirty = true, updatedAt = System.currentTimeMillis())
        budgetDao.insert(dirty)
        syncQueue.enqueue("budget", "insert", dirty)
        return dirty
    }

    suspend fun updateBudget(budget: Budget) {
        val dirty = budget.copy(dirty = true, updatedAt = System.currentTimeMillis())
        budgetDao.update(dirty)
        syncQueue.enqueue("budget", "update", dirty)
    }

    suspend fun deleteBudget(budget: Budget) {
        val soft = budget.copy(deleted = true, dirty = true, updatedAt = System.currentTimeMillis())
        budgetDao.update(soft)
        syncQueue.enqueue("budget", "delete", soft)
    }

    suspend fun autoCategorize(transaction: Transaction): Transaction {
        val desc = transaction.description.lowercase()
        val cats = categoryRepository.categories.first()
        val catMap = cats.associateBy { it.name.lowercase() }
        val rules = listOf(
            listOf("salary", "payroll", "direct deposit") to "Income",
            listOf("rent", "mortgage") to "Housing",
            listOf("grocery", "restaurant", "doordash", "uber eats") to "Food",
            listOf("gas", "uber", "lyft") to "Transportation",
            listOf("netflix", "spotify", "hulu", "disney") to "Entertainment",
            listOf("electric", "water", "internet") to "Utilities",
            listOf("doctor", "pharmacy") to "Healthcare"
        )
        for ((keywords, catName) in rules) {
            if (keywords.any { desc.contains(it) }) {
                val cat = catMap[catName.lowercase()]
                if (cat != null) return transaction.copy(categoryId = cat.id)
            }
        }
        return transaction
    }

    suspend fun getNetWorth(): Double {
        val accountsList = accounts.first()
        return accountsList.sumOf { it.balance }
    }
}
