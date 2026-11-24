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
import com.spendingsaver.data.Account
import com.spendingsaver.ui.components.*
import com.spendingsaver.ui.viewmodel.MainViewModel
import com.spendingsaver.util.Format

@Composable
fun AccountsScreen(navController: NavHostController, viewModel: MainViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val showBalance = settings.showBalance
    val defaultCurrency = settings.defaultCurrency
    val colorScheme = MaterialTheme.colorScheme

    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Checking") }
    var balance by remember { mutableStateOf("") }
    var currency by remember(defaultCurrency) { mutableStateOf(defaultCurrency) }
    var nameError by remember { mutableStateOf(false) }
    var balanceError by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val totalBalance = accounts.sumOf { it.balance }
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(colorScheme.surface, colorScheme.surface.copy(alpha = 0.95f), colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    )

    // One scrolling list for the whole screen, so the form and its button are always reachable
    // (also with the keyboard open or a large text size).
    LazyColumn(
        Modifier.fillMaxSize().background(backgroundGradient).imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            HeroCard(
                title = "Total Balance",
                value = if (showBalance) Format.money(totalBalance, defaultCurrency) else "••••",
                subtitle = "${accounts.size} ${if (accounts.size == 1) "account" else "accounts"}",
                accent = colorScheme.primary,
                onAccent = colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SectionHeader("Add account")
        }
        item {
            AppCard(elevation = 2.dp) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false; message = null },
                    label = { Text("Account name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.AccountBalance, null) },
                    isError = nameError,
                    supportingText = { if (nameError) Text("Give your account a name") },
                )
                Spacer(Modifier.height(8.dp))
                ComboField(
                    label = "Type (pick one or type your own)",
                    value = type,
                    onValueChange = { type = it },
                    suggestions = Choices.ACCOUNT_TYPES,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = balance,
                        onValueChange = { balance = it; balanceError = false; message = null },
                        label = { Text("Starting balance") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = balanceError,
                        supportingText = { Text(if (balanceError) "Enter a number" else "Blank = 0, negative for debt") },
                    )
                    ComboField(
                        label = "Currency",
                        value = currency,
                        onValueChange = { currency = it.uppercase().take(3) },
                        suggestions = Choices.CURRENCIES,
                        modifier = Modifier.weight(0.7f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Always tappable: a missing field is explained instead of the button silently doing nothing.
                PrimaryButton(
                    "Add account",
                    onClick = {
                        nameError = name.isBlank()
                        val amount = if (balance.isBlank()) 0.0 else parseAmount(balance)
                        balanceError = amount == null
                        if (nameError || balanceError) return@PrimaryButton
                        viewModel.addAccount(name, type.ifBlank { "Other" }, amount ?: 0.0, currency.ifBlank { defaultCurrency })
                        message = "Added \"${name.trim()}\""
                        name = ""; balance = ""; type = "Checking"
                    },
                )
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionHeader("Accounts")
        }
        if (accounts.isEmpty()) {
            item {
                EmptyState(emoji = "🏦", title = "No accounts yet", message = "Fill in the form above and tap Add account.")
            }
        } else {
            items(accounts, key = { it.id }) { account ->
                AccountRow(account, showBalance, account.currency.ifBlank { defaultCurrency }, onDelete = { viewModel.deleteAccount(account) })
            }
        }
    }
}

@Composable
fun AccountRow(account: Account, showBalance: Boolean, currency: String, onDelete: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), elevation = 1.dp) {
        ListItem(
            title = account.name,
            subtitle = account.type.replaceFirstChar { it.uppercase() },
            trailing = if (showBalance) Format.money(account.balance, currency) else "••••",
            trailingColor = if (account.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            onDelete = onDelete,
            leadingIcon = {
                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            },
        )
    }
}
