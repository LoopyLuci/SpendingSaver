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

    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("checking") }
    var balance by remember { mutableStateOf("") }
    var balanceError by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(true) }

    val totalBalance = accounts.sumOf { it.balance }
    val colorScheme = MaterialTheme.colorScheme

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
            .padding(16.dp),
    ) {
        // Hero card with total balance
        HeroCard(
            title = "Total Balance",
            value = if (showBalance) Format.money(totalBalance, defaultCurrency) else "\u2022\u2022\u2022\u2022",
            subtitle = "${accounts.size} ${if (accounts.size == 1) "account" else "accounts"}",
            accent = colorScheme.primary,
            onAccent = colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        // Add account form
        if (showForm) {
            SectionHeader("Add account")
            Spacer(Modifier.height(8.dp))
            AppCard(elevation = 2.dp) {
                val nameState = remember { mutableStateOf("") }
                val balanceState = remember { mutableStateOf("") }
                var nameError by remember { mutableStateOf(false) }
                var balanceError by remember { mutableStateOf(false) }
                var snackbarMsg by remember { mutableStateOf<String?>(null) }

                OutlinedTextField(
                    value = nameState.value,
                    onValueChange = { nameState.value = it; nameError = false },
                    label = { Text("Account name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.AccountBalance, null) },
                    isError = nameError,
                    supportingText = {
                        if (nameError) Text("Required — give your account a name")
                        else null
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text("Type") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = balanceState.value,
                        onValueChange = { balanceState.value = it; balanceError = false },
                        label = { Text("Initial balance") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        isError = balanceError,
                        supportingText = {
                            if (balanceError) Text("Enter a valid number, or leave blank for 0")
                            else null
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    "Add account",
                    enabled = nameState.value.isNotBlank(),
                    onClick = {
                        nameError = nameState.value.isBlank()
                        if (nameError) return@PrimaryButton
                        val bal = balanceState.value.toDoubleOrNull()
                        balanceError = balanceState.value.isNotBlank() && bal == null
                        if (balanceError) return@PrimaryButton
                        viewModel.addAccount(nameState.value, type, bal ?: 0.0)
                        nameState.value = ""
                        balanceState.value = ""
                        snackbarMsg = "Account added"
                    },
                )
                if (snackbarMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(snackbarMsg!!, style = MaterialTheme.typography.bodySmall, color = colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Accounts list
        SectionHeader("Accounts")
        Spacer(Modifier.height(4.dp))
        if (accounts.isEmpty()) {
            EmptyState(
                emoji = "🏦",
                title = "No accounts yet",
                message = "Add your first account to start tracking your finances.",
                action = {
                    PrimaryButton(
                        "Add account",
                        onClick = { showForm = true },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                },
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(accounts) { account ->
                    AccountRow(account, showBalance, defaultCurrency, onDelete = { viewModel.deleteAccount(account) })
                }
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
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}
