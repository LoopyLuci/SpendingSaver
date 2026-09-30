package com.spendingsaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.spendingsaver.ui.components.*
import com.spendingsaver.ui.viewmodel.MainViewModel
import com.spendingsaver.util.Format

@Composable
fun NetWorthScreen(navController: NavHostController, viewModel: MainViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val showBalance = settings.showBalance
    val currency = settings.defaultCurrency

    val totalAssets = accounts.filter { it.balance >= 0 }.sumOf { it.balance }
    val totalLiabilities = accounts.filter { it.balance < 0 }.sumOf { Math.abs(it.balance) }
    val netWorth = totalAssets - totalLiabilities

    val colorScheme = MaterialTheme.colorScheme
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        HeroCard(
            title = "Net Worth",
            value = if (showBalance) Format.money(netWorth, currency) else "••••",
            subtitle = if (showBalance) "${accounts.size} ${if (accounts.size == 1) "account" else "accounts"}" else null,
            accent = colorScheme.primary,
            onAccent = colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Assets",
                if (showBalance) Format.money(totalAssets, currency) else "••••",
                Modifier.weight(1f),
                valueColor = colorScheme.primary,
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
                "Liabilities",
                if (showBalance) Format.money(totalLiabilities, currency) else "••••",
                Modifier.weight(1f),
                valueColor = colorScheme.error,
                icon = {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
        Spacer(Modifier.height(20.dp))
        if (accounts.isEmpty()) {
            EmptyState(
                emoji = "🏦",
                title = "No accounts yet",
                message = "Add accounts to see your net worth here.",
                action = {
                    PrimaryButton(
                        "Add account",
                        onClick = { navController.navigate("accounts") },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                },
            )
        } else {
            SectionHeader("Accounts")
            Spacer(Modifier.height(4.dp))
            accounts.forEach { account ->
                AppCard(Modifier.fillMaxWidth().padding(vertical = 3.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    ListItem(
                        title = account.name,
                        subtitle = account.type.replaceFirstChar { it.uppercase() },
                        trailing = if (showBalance) Format.money(account.balance, currency) else "••••",
                        trailingColor = if (account.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
