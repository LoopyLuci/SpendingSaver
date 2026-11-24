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
    var categoryError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf("monthly") }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundGradient = Brush.verticalGradient(colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f)))

    LazyColumn(
        Modifier.fillMaxSize().background(backgroundGradient).imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SectionHeader("Budgets", modifier = Modifier.weight(1f))
                TextButton(onClick = { showForm = !showForm }) {
                    Text(if (showForm) "Cancel" else "Create budget", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }
        if (showForm) {
            item {
                AppCard(elevation = 2.dp) {
                    Text("New budget", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    SelectField(
                        label = "Category",
                        options = categories.map { Option(it.id, it.name) },
                        selectedKey = selectedCategoryId,
                        onSelect = { selectedCategoryId = it; categoryError = false },
                        placeholder = "Select category",
                        isError = categoryError,
                        errorText = "Pick a category",
                        emptyHint = "No categories yet — tap to create one",
                        onEmptyClick = { navController.navigate("categories") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it; amountError = false },
                            label = { Text("Limit") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            isError = amountError,
                            supportingText = { if (amountError) Text("Enter a positive number") },
                        )
                        SelectField(
                            label = "Period",
                            options = Choices.PERIODS.map { Option(it, it.replaceFirstChar { c -> c.uppercase() }) },
                            selectedKey = period,
                            onSelect = { period = it ?: "monthly" },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        "Save budget",
                        onClick = {
                            val amt = parseAmount(amount)
                            categoryError = selectedCategoryId == null
                            amountError = amt == null || amt <= 0
                            if (categoryError || amountError) return@PrimaryButton
                            viewModel.addBudget(categoryId = selectedCategoryId!!, amount = amt!!, period = period, startDate = "")
                            amount = ""; selectedCategoryId = null; showForm = false
                        },
                    )
                }
            }
        }
        if (budgets.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🎯", title = "No budgets yet", message = "Create a budget to track your spending by category.",
                    action = { PrimaryButton("Create budget", onClick = { showForm = true }, modifier = Modifier.padding(horizontal = 24.dp)) },
                )
            }
        } else {
            items(budgets, key = { it.id }) { budget ->
                BudgetRow(budget, categories, transactions, settings, onDelete = { viewModel.deleteBudget(budget) })
            }
        }
    }
}

@Composable
private fun BudgetRow(budget: com.spendingsaver.data.Budget, categories: List<com.spendingsaver.data.Category>, transactions: List<com.spendingsaver.data.Transaction>, settings: com.spendingsaver.data.AppSettings, onDelete: () -> Unit) {
    val category = categories.find { it.id == budget.categoryId }
    val spent = com.spendingsaver.util.BudgetPeriod.spent(transactions, budget.categoryId, budget.period)
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
