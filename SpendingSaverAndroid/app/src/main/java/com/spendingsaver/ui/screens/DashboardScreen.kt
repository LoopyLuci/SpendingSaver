package com.spendingsaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.spendingsaver.ui.components.*
import com.spendingsaver.ui.viewmodel.MainViewModel
import com.spendingsaver.util.Format

@Composable
fun DashboardScreen(navController: NavHostController, viewModel: MainViewModel = hiltViewModel()) {
    val transactions by viewModel.transactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val currency = settings.defaultCurrency
    val showBalance = settings.showBalance

    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    val expenses = transactions.filter { it.amount < 0 }.sumOf { Math.abs(it.amount) }
    val netWorth = accounts.sumOf { it.balance }
    val savings = income - expenses

    // Spending by category for a mini breakdown.
    val byCategory = transactions.filter { it.amount < 0 }.groupBy { it.categoryId }
        .map { (cid, list) ->
            val cat = categories.find { it.id == cid }
            Triple(cat?.name ?: "Uncategorized", cat?.id, list.sumOf { Math.abs(it.amount) })
        }
        .sortedByDescending { it.third }
        .take(5)
    val maxSpend = (byCategory.firstOrNull()?.third ?: 1f).toFloat()

    val colorScheme = MaterialTheme.colorScheme

    // Subtle background gradient for depth
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            colorScheme.surface,
            colorScheme.surface.copy(alpha = 0.95f),
            colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Hero net worth card with gradient background
        HeroCard(
            title = "NET WORTH",
            value = if (showBalance) Format.money(netWorth, currency) else "••••",
            subtitle = if (showBalance) {
                "Income ${Format.money(income, currency)} · Saved ${Format.money(savings, currency)}"
            } else {
                "Balances hidden · change in Settings"
            },
            accent = colorScheme.primary,
            onAccent = colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        // Quick actions with better spacing and visual weight
        QuickActionRow(navController)

        Spacer(Modifier.height(20.dp))

        // Income / Expenses tiles with improved visual hierarchy
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Income",
                if (showBalance) Format.money(income, currency) else "••••",
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
                if (showBalance) Format.money(expenses, currency) else "••••",
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

        Spacer(Modifier.height(20.dp))

        // Account overview counts with better visual treatment
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Accounts",
                "${accounts.size}",
                Modifier.weight(1f),
                icon = {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            StatTile(
                "Transactions",
                "${transactions.size}",
                Modifier.weight(1f),
                icon = {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }

        if (byCategory.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeader(
                "Spending by category",
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            byCategory.forEach { (name, _, spent) ->
                CategoryProgressRow(name, spent, maxSpend, currency, showBalance)
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader(
            "Recent activity",
            trailing = {
                TextButton(
                    onClick = { navController.navigate("transactions") },
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary),
                ) {
                    Text("See all", fontWeight = FontWeight.SemiBold)
                }
            },
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(4.dp))

        if (transactions.isEmpty()) {
            EmptyState(
                emoji = "📊",
                title = "No transactions yet",
                message = "Record your first income or expense to see it here.",
                action = {
                    PrimaryButton(
                        "Add transaction",
                        onClick = { navController.navigate("transactions") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                },
            )
        } else {
            transactions.take(6).forEach { tx ->
                val cat = categories.find { it.id == tx.categoryId }
                TransactionRow(tx, cat?.name, currency, showBalance, settings.dateFormat)
            }
        }
    }
}

@Composable
private fun CategoryProgressRow(name: String, spent: Double, maxSpend: Float, currency: String, showBalance: Boolean) {
    val fraction = if (maxSpend > 0f) (spent / maxSpend).toFloat().coerceIn(0f, 1f) else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (showBalance) Format.money(spent, currency) else "••••",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuickActionRow(navController: NavHostController) {
    val actions = listOf(
        Triple("Add", Icons.Default.Add, "transactions"),
        Triple("AI", Icons.Default.SmartToy, "ai"),
        Triple("Tax", Icons.Default.Business, "tax"),
        Triple("Budgets", Icons.Default.Savings, "budgets"),
        Triple("Accounts", Icons.Default.AccountBalance, "accounts"),
    )
    Row(Modifier.fillMaxWidth()) {
        actions.forEach { (label, icon, route) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { navController.navigate(route) }
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
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
) {
    AppCard(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        ListItem(
            title = tx.description,
            subtitle = "${category ?: "Uncategorized"} • ${Format.date(tx.date, dateFormat)}",
            trailing = if (showBalance) Format.money(tx.amount, currency) else "••••",
            trailingColor = if (tx.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
