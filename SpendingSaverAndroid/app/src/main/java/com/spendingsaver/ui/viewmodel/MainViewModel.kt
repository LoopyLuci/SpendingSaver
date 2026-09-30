package com.spendingsaver.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingsaver.data.*
import com.spendingsaver.repository.BudgetRepository
import com.spendingsaver.repository.SettingsRepository
import com.spendingsaver.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: BudgetRepository,
    private val settingsRepo: SettingsRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    val accounts = repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val transactions = repo.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val budgets = repo.budgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), com.spendingsaver.data.AppSettings())

    fun addAccount(name: String, type: String, balance: Double) {
        viewModelScope.launch {
            repo.addAccount(Account(name = name, type = type, balance = balance))
        }
    }

    fun addCategory(name: String, color: String? = null) {
        viewModelScope.launch {
            repo.addCategory(Category(name = name, color = color))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repo.deleteCategory(category) }
    }

    fun addTransaction(accountId: String, amount: Double, description: String, date: String, categoryId: String?, notes: String?) {
        viewModelScope.launch {
            var tx = Transaction(
                accountId = accountId,
                amount = amount,
                description = description,
                date = date,
                categoryId = categoryId,
                notes = notes
            )
            tx = repo.autoCategorize(tx)
            repo.addTransaction(tx)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch { repo.updateTransaction(transaction) }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repo.deleteTransaction(transaction) }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch { repo.deleteAccount(account) }
    }

    fun addBudget(categoryId: String, amount: Double, period: String, startDate: String) {
        viewModelScope.launch {
            repo.addBudget(Budget(categoryId = categoryId, amount = amount, period = period, startDate = startDate))
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch { repo.deleteBudget(budget) }
    }

    fun importTransactionsFromCsv(accountId: String, transactions: List<Transaction>) {
        viewModelScope.launch {
            for (tx in transactions) {
                val autoCategorized = repo.autoCategorize(tx)
                repo.addTransaction(autoCategorized)
            }
        }
    }

    /** On-demand sync triggered from the UI / app entry point. */
    fun syncNow() {
        SyncScheduler.syncNow(appContext)
    }
}
