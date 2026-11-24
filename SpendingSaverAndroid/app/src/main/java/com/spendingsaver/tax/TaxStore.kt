package com.spendingsaver.tax

import android.content.Context
import android.content.SharedPreferences
import com.spendingsaver.data.Category
import com.spendingsaver.data.Transaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the user's own transactions say about one tax year. */
data class TaxYearSummary(
    val year: Int,
    val income: Double,
    val expenses: Double,
    val deductible: Double,
    val deductibleByCategory: List<Pair<String, Double>>,
    val transactionCount: Int,
)

object TaxSummary {
    fun forYear(year: Int, transactions: List<Transaction>, categories: List<Category>, deductibleIds: Set<String>): TaxYearSummary {
        val prefix = "$year-"
        val tx = transactions.filter { !it.deleted && it.date.startsWith(prefix) }
        val names = categories.associate { it.id to it.name }
        val income = tx.filter { it.amount > 0 }.sumOf { it.amount }
        val expenses = tx.filter { it.amount < 0 }.sumOf { -it.amount }
        val byCat = tx.filter { it.amount < 0 && it.categoryId in deductibleIds }
            .groupBy { it.categoryId }
            .map { (id, l) -> (names[id] ?: "Uncategorized") to l.sumOf { -it.amount } }
            .sortedByDescending { it.second }
        return TaxYearSummary(year, income, expenses, byCat.sumOf { it.second }, byCat, tx.size)
    }

    /** A plain-text report the user can share or keep. */
    fun report(p: TaxProfile, r: TaxResult, s: TaxYearSummary, money: (Double) -> String): String = buildString {
        appendLine("SpendingSaver tax estimate, ${p.year} (US federal${if (p.state.isNotBlank()) " + ${States.label(p.state)}" else ""})")
        appendLine("An estimate for planning only, not tax advice or a filing.")
        appendLine()
        appendLine("Filing status: ${p.filing.label}   Children under 17: ${p.children}")
        appendLine("Gross income: ${money(r.grossIncome)}")
        appendLine("Adjusted gross income: ${money(r.adjustedGross)}")
        appendLine("${if (p.useItemized) "Itemized" else "Standard"} deduction: ${money(r.deduction)}")
        appendLine("Taxable income: ${money(r.taxableIncome)}")
        appendLine("Federal income tax: ${money(r.incomeTax)} (marginal ${(r.marginalRate * 100).toInt()}%)")
        if (r.childCredit > 0) appendLine("  includes child tax credit of ${money(r.childCredit)}")
        if (r.selfEmploymentTax > 0) appendLine("Self-employment tax: ${money(r.selfEmploymentTax)}")
        if (r.additionalMedicare > 0) appendLine("Additional Medicare tax: ${money(r.additionalMedicare)}")
        if (r.stateTax > 0) appendLine("State tax (your rate): ${money(r.stateTax)}")
        appendLine("Total due before payments: ${money(r.dueBeforePayments)}")
        appendLine("Paid so far (withholding + estimates): ${money(r.totalPaid)}")
        appendLine(if (r.balance >= 0) "Still owed: ${money(r.balance)}" else "Estimated refund: ${money(-r.balance)}")
        appendLine("Effective rate incl. payroll tax: ${"%.1f".format(r.effectiveRate * 100)}%")
        appendLine("Suggested quarterly payment: ${money(r.quarterly)}")
        appendLine()
        appendLine("From your transactions in ${s.year}: income ${money(s.income)}, spending ${money(s.expenses)}, tagged deductible ${money(s.deductible)}")
        s.deductibleByCategory.forEach { appendLine("  ${it.first}: ${money(it.second)}") }
    }
}

/** Remembers the tax profile and which categories count as deductible. Stored only on this device. */
@Singleton
class TaxStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("spendingsaver_tax", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<TaxProfile> = _profile.asStateFlow()

    private val _deductible = MutableStateFlow(prefs.getStringSet(KEY_DEDUCTIBLE, emptySet()).orEmpty().toSet())
    val deductibleCategoryIds: StateFlow<Set<String>> = _deductible.asStateFlow()

    private fun loadProfile() = TaxProfile(
        year = prefs.getInt("year", TaxTables.SUPPORTED_YEARS.first()).takeIf { it in TaxTables.YEARS } ?: TaxTables.SUPPORTED_YEARS.first(),
        filing = FilingStatus.values().firstOrNull { it.name == prefs.getString("filing", "") } ?: FilingStatus.SINGLE,
        wages = d("wages"), selfEmployment = d("se"), otherIncome = d("other"), adjustments = d("adj"),
        useItemized = prefs.getBoolean("useItemized", false), itemized = d("itemized"),
        children = prefs.getInt("children", 0),
        federalWithheld = d("withheld"), estimatedPaid = d("estPaid"),
        state = prefs.getString("state", "") ?: "", stateRatePercent = d("stateRate"),
    )

    private fun d(key: String) = prefs.getString(key, null)?.toDoubleOrNull() ?: 0.0

    fun save(p: TaxProfile) {
        _profile.value = p
        prefs.edit()
            .putInt("year", p.year).putString("filing", p.filing.name)
            .putString("wages", p.wages.toString()).putString("se", p.selfEmployment.toString())
            .putString("other", p.otherIncome.toString()).putString("adj", p.adjustments.toString())
            .putBoolean("useItemized", p.useItemized).putString("itemized", p.itemized.toString())
            .putInt("children", p.children)
            .putString("withheld", p.federalWithheld.toString()).putString("estPaid", p.estimatedPaid.toString())
            .putString("state", p.state).putString("stateRate", p.stateRatePercent.toString())
            .apply()
    }

    fun setDeductible(categoryId: String, on: Boolean) {
        val next = if (on) _deductible.value + categoryId else _deductible.value - categoryId
        _deductible.value = next
        prefs.edit().putStringSet(KEY_DEDUCTIBLE, next).apply()
    }

    private companion object { const val KEY_DEDUCTIBLE = "deductible_categories" }
}
