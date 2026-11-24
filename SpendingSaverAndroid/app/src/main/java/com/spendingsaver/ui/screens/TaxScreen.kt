package com.spendingsaver.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingsaver.tax.*
import com.spendingsaver.ui.components.*
import com.spendingsaver.ui.viewmodel.TaxViewModel
import com.spendingsaver.util.Format

private val TABS = listOf("Estimate", "Deductions", "Payments", "Report")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxScreen(viewModel: TaxViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val deductibleIds by viewModel.deductibleIds.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val money = { v: Double -> Format.money(v, settings.defaultCurrency) }
    var tab by remember { mutableStateOf(0) }

    val result = remember(profile) { TaxEngine.estimate(profile) }
    val summary = remember(profile.year, transactions, categories, deductibleIds) {
        TaxSummary.forYear(profile.year, transactions, categories, deductibleIds)
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> EstimateTab(profile, result, summary, money, onChange = viewModel::save)
            1 -> DeductionsTab(profile, categories, transactions, deductibleIds, summary, money, viewModel::setDeductible) {
                viewModel.save(profile.copy(useItemized = true, itemized = summary.deductible)); tab = 0
            }
            2 -> PaymentsTab(profile, result, money)
            else -> ReportTab(profile, result, summary, money)
        }
    }
}

@Composable
private fun Disclaimer() {
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            "Planning estimate for US federal tax, worked out on this phone with no internet. It is not tax advice or a filing, " +
                "and it leaves out the business-income deduction, AMT, capital-gain rates and most credits. State tax uses the rate you enter.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** An amount box that keeps what the user typed and reports a parsed value. */
@Composable
private fun AmountBox(label: String, value: Double, onValue: (Double) -> Unit, help: String? = null) {
    var text by remember { mutableStateOf(if (value == 0.0) "" else plain(value)) }
    val parsed = parseAmount(text)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onValue(if (it.isBlank()) 0.0 else parseAmount(it)?.coerceAtLeast(0.0) ?: value) },
        label = { Text(label) },
        singleLine = true,
        isError = text.isNotBlank() && parsed == null,
        supportingText = { if (text.isNotBlank() && parsed == null) Text("Enter a number") else if (help != null) Text(help) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EstimateTab(profile: TaxProfile, r: TaxResult, summary: TaxYearSummary, money: (Double) -> String, onChange: (TaxProfile) -> Unit) {
    val noTaxState = profile.state in States.NO_INCOME_TAX
    // Fields are re-created when a value is replaced from outside (e.g. "Use my transactions").
    var epoch by remember { mutableStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Disclaimer() }
        item {
            val owes = r.balance >= 0
            HeroCard(
                title = if (owes) "Estimated tax still owed" else "Estimated refund",
                value = money(Math.abs(r.balance)),
                subtitle = "${profile.year} · total due ${money(r.dueBeforePayments)} · effective ${"%.1f".format(r.effectiveRate * 100)}%",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Taxable income", money(r.taxableIncome), Modifier.weight(1f))
                StatTile("Marginal rate", "${(r.marginalRate * 100).toInt()}%", Modifier.weight(1f))
            }
        }
        item { SectionHeader("About you") }
        item {
            SelectField("Tax year", TaxTables.SUPPORTED_YEARS.map { Option(it.toString(), it.toString()) }, profile.year.toString(),
                { it?.let { y -> onChange(profile.copy(year = y.toInt())) } })
        }
        item {
            SelectField("Filing status", FilingStatus.values().map { Option(it.name, it.label) }, profile.filing.name,
                { k -> k?.let { onChange(profile.copy(filing = FilingStatus.valueOf(it))) } })
        }
        item {
            ComboField("Children under 17", profile.children.toString(), { v -> onChange(profile.copy(children = v.filter(Char::isDigit).take(2).toIntOrNull() ?: 0)) },
                suggestions = (0..6).map { it.toString() }, keyboardType = KeyboardType.Number)
        }
        item { SectionHeader("Income for ${profile.year}") }
        item {
            OutlinedButton(
                onClick = { onChange(profile.copy(wages = summary.income)); epoch++ },
                enabled = summary.income > 0.0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (summary.income > 0) "Use my ${profile.year} deposits as wages (${money(summary.income)})" else "No ${profile.year} deposits recorded yet") }
        }
        item { key(epoch) { AmountBox("Wages (W-2)", profile.wages, { onChange(profile.copy(wages = it)) }) } }
        item { key(epoch) { AmountBox("Self-employment profit", profile.selfEmployment, { onChange(profile.copy(selfEmployment = it)) }, "Net of business expenses") } }
        item { key(epoch) { AmountBox("Other taxable income", profile.otherIncome, { onChange(profile.copy(otherIncome = it)) }, "Interest, dividends, side income") } }
        item { key(epoch) { AmountBox("Adjustments", profile.adjustments, { onChange(profile.copy(adjustments = it)) }, "IRA or HSA contributions, student loan interest") } }
        item { SectionHeader("Deductions") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Itemize instead of standard", fontWeight = FontWeight.Medium)
                    Text("Standard: ${money(TaxTables.forYear(profile.year).standardDeduction.getValue(profile.filing))}", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = profile.useItemized, onCheckedChange = { onChange(profile.copy(useItemized = it)) })
            }
        }
        if (profile.useItemized) item { key(epoch) { AmountBox("Itemized total", profile.itemized, { onChange(profile.copy(itemized = it)) }) } }
        item { SectionHeader("State") }
        item {
            SelectField("State", States.ALL.map { Option(it.first, "${it.second} (${it.first})") }, profile.state.ifBlank { null },
                { onChange(profile.copy(state = it ?: "")) }, noneLabel = "Not set / outside the US", placeholder = "Not set")
        }
        item {
            if (noTaxState) Text("${States.label(profile.state)} has no tax on wages.", style = MaterialTheme.typography.bodySmall)
            else key(epoch, profile.state) {
                var text by remember { mutableStateOf(if (profile.stateRatePercent == 0.0) "" else plain(profile.stateRatePercent)) }
                ComboField("State tax rate (%)", text, { text = it; onChange(profile.copy(stateRatePercent = parseAmount(it)?.coerceIn(0.0, 25.0) ?: 0.0)) },
                    suggestions = listOf("0", "2", "3", "4", "5", "6", "7", "8", "9", "10"), keyboardType = KeyboardType.Decimal,
                    supportingText = "Your effective state rate on taxable income")
            }
        }
        item { SectionHeader("Already paid") }
        item { key(epoch) { AmountBox("Federal tax withheld", profile.federalWithheld, { onChange(profile.copy(federalWithheld = it)) }, "From your pay stubs or W-2") } }
        item { key(epoch) { AmountBox("Estimated payments made", profile.estimatedPaid, { onChange(profile.copy(estimatedPaid = it)) }) } }
        item { SectionHeader("Where the tax comes from") }
        item {
            AppCard {
                Line("Federal income tax", money(r.incomeTax))
                if (r.childCredit > 0) Line("  after child credit of", money(r.childCredit))
                if (r.selfEmploymentTax > 0) Line("Self-employment tax", money(r.selfEmploymentTax))
                if (r.additionalMedicare > 0) Line("Additional Medicare tax", money(r.additionalMedicare))
                if (r.stateTax > 0) Line("State tax", money(r.stateTax))
                Line("Payroll tax on wages (withheld)", money(r.employeeFica))
            }
        }
        item { SectionHeader("Brackets") }
        item {
            AppCard {
                r.brackets.filter { it.taxed > 0 }.ifEmpty { listOf() }.forEach {
                    Line("${(it.rate * 100).toInt()}% on ${money(it.taxed)}", money(it.tax))
                }
                if (r.taxableIncome <= 0.0) Text("No taxable income after the deduction.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun DeductionsTab(
    profile: TaxProfile,
    categories: List<com.spendingsaver.data.Category>,
    transactions: List<com.spendingsaver.data.Transaction>,
    deductibleIds: Set<String>,
    summary: TaxYearSummary,
    money: (Double) -> String,
    onToggle: (String, Boolean) -> Unit,
    onUse: () -> Unit,
) {
    val live = categories.filter { !it.deleted }.sortedBy { it.name.lowercase() }
    val spent = remember(transactions, profile.year) {
        transactions.filter { !it.deleted && it.amount < 0 && it.date.startsWith("${profile.year}-") }
            .groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { -it.amount } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Disclaimer() }
        item {
            HeroCard("Tagged deductible in ${profile.year}", money(summary.deductible), "Spending in the categories switched on below")
        }
        item {
            Button(onClick = onUse, enabled = summary.deductible > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Use as my itemized total")
            }
        }
        item { Text("Switch on the categories that count as deductible (medical, charity, business costs, and so on).", style = MaterialTheme.typography.bodySmall) }
        if (live.isEmpty()) item { EmptyState("🏷️", "No categories yet", "Create categories first, then tag the deductible ones here.") }
        items(live, key = { it.id }) { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.Medium)
                    Text("${profile.year}: ${money(spent[c.id] ?: 0.0)}", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = c.id in deductibleIds, onCheckedChange = { onToggle(c.id, it) })
            }
        }
    }
}

@Composable
private fun PaymentsTab(profile: TaxProfile, r: TaxResult, money: (Double) -> String) {
    val dates = TaxEngine.quarterlyDates(profile.year)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Disclaimer() }
        item { HeroCard("Suggested payment each quarter", money(r.quarterly), "Total due less withholding, split in four") }
        item {
            AppCard {
                dates.forEachIndexed { i, d -> Line("Payment ${i + 1} · $d", money(r.quarterly)) }
                Line("Paid in estimates so far", money(profile.estimatedPaid))
                Line("Withheld so far", money(profile.federalWithheld))
            }
        }
        item {
            Text(
                "Employees whose pay is fully withheld usually pay nothing quarterly. Self-employed people and people with other income " +
                    "often must. Dates shift to the next business day when they fall on a weekend or holiday. Pay through IRS Direct Pay or your state's site.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReportTab(profile: TaxProfile, r: TaxResult, s: TaxYearSummary, money: (Double) -> String) {
    val context = LocalContext.current
    val text = remember(profile, r, s) { TaxSummary.report(profile, r, s, money) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Tax estimate ${profile.year}"); putExtra(Intent.EXTRA_TEXT, text) }
                context.startActivity(Intent.createChooser(send, "Share tax estimate"))
            }, modifier = Modifier.fillMaxWidth()) { Text("Share or save this report") }
        }
        item { AppCard { Text(text, style = MaterialTheme.typography.bodySmall) } }
    }
}

private fun plain(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(java.util.Locale.US, v)
