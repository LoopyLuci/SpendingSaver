package com.spendingsaver.ai

import com.spendingsaver.repository.BudgetRepository
import com.spendingsaver.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers questions about the user's finances on the device. It depends only on the local
 * repositories: there is no HTTP client, URL or service anywhere in this package.
 */
@Singleton
class LocalAssistant @Inject constructor(
    private val repo: BudgetRepository,
    private val settings: SettingsRepository,
) {
    suspend fun ask(question: String): String {
        val snapshot = FinanceSnapshot(
            accounts = repo.accounts.first(),
            categories = repo.categories.first(),
            transactions = repo.transactions.first(),
            budgets = repo.budgets.first(),
            settings = settings.settings.value,
        )
        return AssistantEngine.answer(question, snapshot)
    }
}
