package com.spendingsaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.spendingsaver.ui.components.*
import com.spendingsaver.ui.viewmodel.MainViewModel
import com.spendingsaver.util.Format
import java.text.SimpleDateFormat
import java.util.*

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
    var selectedAccountId by remember { mutableStateOf("") }
    var accountError by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var dateError by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var accExpanded by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }

    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    val expenses = transactions.filter { it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    )

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Income",
                    Format.money(income, settings.defaultCurrency),
                    Modifier.weight(1f),
                    valueColor = colorScheme.primary,
                    icon = {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                StatTile(
                    "Expenses",
                    Format.money(expenses, settings.defaultCurrency),
                    Modifier.weight(1f),
                    valueColor = colorScheme.error,
                    icon = {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            if (showForm) {
                SectionHeader("New transaction")
                Spacer(Modifier.height(8.dp))
                AppCard(elevation = 2.dp) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it; descriptionError = false },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        isError = descriptionError,
                        supportingText = { if (descriptionError) Text("Required") else null },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it; amountError = false },
                            label = { Text("Amount") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            isError = amountError,
                            supportingText = { if (amountError) Text("Enter a valid number") else null },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                        )
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it; dateError = false },
                            label = { Text("Date") },
                            modifier = Modifier.weight(1f),
                            isError = dateError,
                            supportingText = { if (dateError) Text("Required") else null },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = accExpanded,
                        onExpandedChange = { accExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = accounts.find { it.id == selectedAccountId }?.name ?: "Select account",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                            modifier = Modifier.weight(1f).menuAnchor(),
                            isError = accountError,
                            supportingText = { if (accountError) Text("Required") else null },
                        )
                        ExposedDropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = { selectedAccountId = account.id; accExpanded = false; accountError = false },
                                )
                            }
                            if (accounts.isEmpty()) {
                                DropdownMenuItem(text = { Text("No accounts — add one first") }, onClick = { accExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = catExpanded,
                        onExpandedChange = { catExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = categories.find { it.id == selectedCategoryId }?.name ?: "Select category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                            DropdownMenuItem(text = { Text("Auto categorize") }, onClick = { selectedCategoryId = null; catExpanded = false })
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = { selectedCategoryId = cat.id; catExpanded = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Save transaction",
                        onClick = {
                            descriptionError = description.isBlank()
                            amountError = amount.isBlank() || amount.toDoubleOrNull() == null
                            accountError = selectedAccountId.isBlank()
                            dateError = date.isBlank()
                            if (descriptionError || amountError || accountError || dateError) return@PrimaryButton
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            viewModel.addTransaction(accountId = selectedAccountId, amount = amt, description = description, date = date, categoryId = selectedCategoryId, notes = notes)
                            description = ""
                            amount = ""
                            selectedAccountId = ""
                            selectedCategoryId = null
                            notes = ""
                            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            showForm = false
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SectionHeader("Transactions", modifier = Modifier.weight(1f))
                if (transactions.isNotEmpty()) {
                    TextButton(onClick = { /* could add filter */ }) {
                        Text("Filter", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            if (transactions.isEmpty()) {
                EmptyState(
                    emoji = "🧾",
                    title = "No transactions yet",
                    message = "Tap + to record your first income or expense.",
                    action = {
                        PrimaryButton(
                            "Add transaction",
                            onClick = { showForm = true },
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(transactions) { tx ->
                        val cat = categories.find { it.id == tx.categoryId }
                        TransactionRow(
                            tx = tx,
                            category = cat?.name,
                            currency = settings.defaultCurrency,
                            showBalance = settings.showBalance,
                            dateFormat = settings.dateFormat,
                            onDelete = { viewModel.deleteTransaction(tx) },
                        )
                    }
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
            onClick = onDelete,
            leadingIcon = {
                Icon(
                    if (tx.amount < 0) Icons.Default.ShoppingCart else Icons.Default.AttachMoney,
                    contentDescription = null,
                    tint = if (tx.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}
