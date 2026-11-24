package com.spendingsaver.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingsaver.data.Category
import com.spendingsaver.data.Transaction
import com.spendingsaver.repository.BudgetRepository
import com.spendingsaver.repository.SettingsRepository
import com.spendingsaver.tax.TaxProfile
import com.spendingsaver.tax.TaxStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TaxViewModel @Inject constructor(
    repo: BudgetRepository,
    settingsRepo: SettingsRepository,
    private val store: TaxStore,
) : ViewModel() {
    val profile: StateFlow<TaxProfile> = store.profile
    val deductibleIds: StateFlow<Set<String>> = store.deductibleCategoryIds
    val settings = settingsRepo.settings
    val categories: StateFlow<List<Category>> = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val transactions: StateFlow<List<Transaction>> = repo.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun save(p: TaxProfile) = store.save(p)
    fun setDeductible(categoryId: String, on: Boolean) = store.setDeductible(categoryId, on)
}
