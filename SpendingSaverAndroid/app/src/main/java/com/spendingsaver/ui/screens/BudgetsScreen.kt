package com.spendingsaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.spendingsaver.ui.components.*
import com.spendingsaver.ui.viewmodel.MainViewModel
import com.spendingsaver.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(navController: NavHostController, viewModel: MainViewModel = hiltViewModel()) {
    val budgets by viewModel.budgets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf("monthly") }
    var catExpanded by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionHeader("Budgets", modifier = Modifier.weight(1f))
            TextButton(onClick = { showForm = !showForm }) {
                Text(if (showForm) "Cancel" else "Create budget", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }

        if (showForm) {
            Spacer(Modifier.height(12.dp))
            AppCard(elevation = 2.dp) {
                val amountState = remember { mutableStateOf("") }
                var amountError by remember { mutableStateOf(false) }
                var snackbarMsg by remember { mutableStateOf<String?>(null) }

                Text("New budget", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
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
                        isError = selectedCategoryId == null,
                        supportingText = {
                            if (selectedCategoryId == null) Text("Pick a category first")
                            else null
                        },
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { selectedCategoryId = cat.id; catExpanded = false },
                            )
                        }
                        if (categories.isEmpty()) {
                            DropdownMenuItem(text = { Text("No categories yet") }, onClick = { catExpanded = false })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountState.value,
                    onValueChange = { amountState.value = it; amountError = false },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    isError = amountError,
                    supportingText = {
                        if (amountError) Text("Enter a positive number")
                        else null
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("weekly", "monthly", "yearly").forEach { p ->
                        FilterChip(
                            selected = period == p,
                            onClick = { period = p },
                            label = { Text(p.replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    "Save budget",
                    enabled = selectedCategoryId != null && amountState.value.isNotBlank(),
                    onClick = {
                        amountError = amountState.value.isNotBlank() && amountState.value.toDoubleOrNull() == null
                        if (amountError) return@PrimaryButton
                        val amt = amountState.value.toDoubleOrNull() ?: return@PrimaryButton
                        if (amt <= 0) {
                            amountError = true
                            return@PrimaryButton
                        }
                        viewModel.addBudget(categoryId = selectedCategoryId!!, amount = amt, period = period, startDate = "")
                        amountState.value = ""
                        selectedCategoryId = null
                        showForm = false
                        snackbarMsg = "Budget created"
                    },
                )
                if (snackbarMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(snackbarMsg!!, style = MaterialTheme.typography.bodySmall, color = colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (budgets.isEmpty()) {
            EmptyState(
                emoji = "🎯",
                title = "No budgets yet",
                message = "Create a budget to track your spending by category.",
                action = {
                    PrimaryButton(
                        "Create budget",
                        onClick = { showForm = true },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                },
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(budgets) { budget ->
                    BudgetRow(budget, categories, transactions, settings, onDelete = { viewModel.deleteBudget(budget) })
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(budget: com.spendingsaver.data.Budget, categories: List<com.spendingsaver.data.Category>, transactions: List<com.spendingsaver.data.Transaction>, settings: com.spendingsaver.data.AppSettings, onDelete: () -> Unit) {
    val category = categories.find { it.id == budget.categoryId }
    val spent = transactions.filter { it.categoryId == budget.categoryId && it.amount < 0 }.sumOf { Math.abs(it.amount) }
    // A zero budget would give NaN/Infinity and break the progress bar.
    val fraction = if (budget.amount > 0) (spent / budget.amount).toFloat().coerceIn(0f, 1f) else 0f
    val isOver = spent > budget.amount

    AppCard(Modifier.fillMaxWidth(), elevation = 1.dp) {
        ListItem(
            title = category?.name ?: "Unknown category",
            subtitle = "${budget.period.replaceFirstChar { it.uppercase() }} · ${if (settings.showBalance) Format.money(spent, settings.defaultCurrency) else "••••"} of ${Format.money(budget.amount, settings.defaultCurrency)}",
            trailing = if (settings.showBalance) Format.money(budget.amount - spent, settings.defaultCurrency) else "••••",
            trailingColor = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            onDelete = onDelete,
            leadingIcon = {
                Icon(
                    Icons.Default.Savings,
                    contentDescription = null,
                    tint = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth(),
                color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
