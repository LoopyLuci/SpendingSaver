package com.spendingsaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.spendingsaver.ui.components.*
import com.spendingsaver.ui.viewmodel.MainViewModel
import com.spendingsaver.util.Format
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(navController: NavHostController, viewModel: MainViewModel = hiltViewModel()) {
    val transactions by viewModel.transactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var description by remember { mutableStateOf("") }
    var descriptionError by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var dateError by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }

    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    val expenses = transactions.filter { it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }
    // Earlier descriptions become suggestions, so repeat entries are one tap.
    val descriptionIdeas = remember(transactions) {
        transactions.groupingBy { it.description.trim() }.eachCount().entries
            .filter { it.key.isNotEmpty() }.sortedByDescending { it.value }.map { it.key }.take(40)
    }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundGradient = Brush.verticalGradient(colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f)))

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showForm = !showForm },
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
            ) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = if (showForm) "Close form" else "Add transaction")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(backgroundGradient).padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Income", Format.money(income, settings.defaultCurrency), Modifier.weight(1f), valueColor = colorScheme.primary,
                        icon = { Icon(Icons.Default.ArrowUpward, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp)) },
                    )
                    StatTile(
                        "Expenses", Format.money(expenses, settings.defaultCurrency), Modifier.weight(1f), valueColor = colorScheme.error,
                        icon = { Icon(Icons.Default.ArrowDownward, null, tint = colorScheme.error, modifier = Modifier.size(20.dp)) },
                    )
                }
            }

            if (showForm) {
                item {
                    SectionHeader("New transaction")
                    Spacer(Modifier.height(8.dp))
                    AppCard(elevation = 2.dp) {
                        ComboField(
                            label = "Description (pick a past one or type)",
                            value = description,
                            onValueChange = { description = it; descriptionError = false },
                            suggestions = descriptionIdeas,
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            isError = descriptionError,
                            supportingText = if (descriptionError) "Required" else null,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = isExpense, onClick = { isExpense = true }, label = { Text("Expense") },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null, Modifier.size(18.dp)) })
                            FilterChip(selected = !isExpense, onClick = { isExpense = false }, label = { Text("Income") },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null, Modifier.size(18.dp)) })
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it; amountError = false },
                                label = { Text("Amount") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = amountError,
                                supportingText = { if (amountError) Text("Enter a number") },
                            )
                            DateField(
                                label = "Date", value = date, onValueChange = { date = it; dateError = false },
                                modifier = Modifier.weight(1.2f), isError = dateError, errorText = "Use yyyy-MM-dd",
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        SelectField(
                            label = "Account",
                            options = accounts.map { Option(it.id, it.name) },
                            selectedKey = selectedAccountId,
                            onSelect = { selectedAccountId = it; accountError = false },
                            placeholder = "Select account",
                            isError = accountError,
                            errorText = if (accounts.isEmpty()) "Add an account first" else "Choose an account",
                            emptyHint = "No accounts yet — tap to create one",
                            onEmptyClick = { navController.navigate("accounts") },
                        )
                        Spacer(Modifier.height(8.dp))
                        SelectField(
                            label = "Category",
                            options = categories.map { Option(it.id, it.name) },
                            selectedKey = selectedCategoryId,
                            onSelect = { selectedCategoryId = it },
                            noneLabel = "Auto categorize",
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = notes, onValueChange = { notes = it },
                            label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            text = "Save transaction",
                            onClick = {
                                val parsed = parseAmount(amount)
                                val dateOk = runCatching { LocalDate.parse(date.trim()) }.isSuccess
                                descriptionError = description.isBlank()
                                amountError = parsed == null
                                accountError = selectedAccountId == null
                                dateError = !dateOk
                                if (descriptionError || amountError || accountError || dateError) return@PrimaryButton
                                // Expenses are stored negative; the chip decides the sign, not the typing.
                                val magnitude = kotlin.math.abs(parsed ?: 0.0)
                                viewModel.addTransaction(
                                    accountId = selectedAccountId!!, amount = if (isExpense) -magnitude else magnitude,
                                    description = description.trim(), date = date.trim(), categoryId = selectedCategoryId, notes = notes.ifBlank { null },
                                )
                                description = ""; amount = ""; isExpense = true; selectedAccountId = null
                                selectedCategoryId = null; notes = ""; date = LocalDate.now().toString(); showForm = false
                            },
                        )
                    }
                }
            }

            item { SectionHeader("Transactions") }

            if (transactions.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🧾", title = "No transactions yet", message = "Tap + to record your first income or expense.",
                        action = { PrimaryButton("Add transaction", onClick = { showForm = true }, modifier = Modifier.padding(horizontal = 24.dp)) },
                    )
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    val cat = categories.find { it.id == tx.categoryId }
                    TransactionRow(tx, cat?.name, settings.defaultCurrency, settings.showBalance, settings.dateFormat) { viewModel.deleteTransaction(tx) }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    tx: com.spendingsaver.data.Transaction,
    category: String?,
    currency: String,
    showBalance: Boolean,
    dateFormat: String,
    onDelete: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth(), elevation = 1.dp) {
        ListItem(
            title = tx.description,
            subtitle = "${category ?: "Uncategorized"} • ${Format.date(tx.date, dateFormat)}",
            trailing = if (showBalance) Format.money(tx.amount, currency) else "••••",
            trailingColor = if (tx.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            onDelete = onDelete,
            leadingIcon = {
                Icon(
                    if (tx.amount < 0) Icons.Default.ShoppingCart else Icons.Default.AttachMoney, contentDescription = null,
                    tint = if (tx.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}
