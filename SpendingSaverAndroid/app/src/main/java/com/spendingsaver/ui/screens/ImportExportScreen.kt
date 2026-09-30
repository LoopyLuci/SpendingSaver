package com.spendingsaver.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.spendingsaver.data.Transaction
import com.spendingsaver.ui.components.*
import com.spendingsaver.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(navController: NavHostController, viewModel: MainViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedAccountId by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var accExpanded by remember { mutableStateOf(false) }

    // Import preview state
    var previewRows by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var previewErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPreview by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (selectedAccountId.isBlank()) {
            status = "Select an account first"
            statusIsError = true
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val rows = mutableListOf<Transaction>()
                    val errors = mutableListOf<String>()
                    var lineNum = 0
                    reader.forEachLine { line ->
                        lineNum++
                        if (line.isBlank()) return@forEachLine
                        if (lineNum == 1) {
                            val header = line.lowercase()
                            if (!header.contains("date") || !header.contains("amount")) {
                                errors.add("Header must contain Date and Amount columns")
                            }
                            return@forEachLine
                        }
                        val parts = line.split(",")
                        if (parts.size < 3) {
                            errors.add("Line $lineNum: too few columns")
                            return@forEachLine
                        }
                        val date = parts[0].trim().ifBlank { com.spendingsaver.util.Format.todayIso() }
                        val desc = parts[1].trim().removeSurrounding("\"")
                        val amount = parts[2].trim().toDoubleOrNull()
                        if (amount == null) {
                            errors.add("Line $lineNum: invalid amount '${parts[2].trim()}'")
                            return@forEachLine
                        }
                        rows.add(
                            Transaction(
                                accountId = selectedAccountId,
                                description = desc,
                                amount = amount,
                                date = date,
                            )
                        )
                    }
                    Pair(rows, errors)
                } ?: Pair(mutableListOf(), mutableListOf("Empty file"))
            }.getOrElse { Pair(mutableListOf(), mutableListOf("Read error: ${it.message}")) }

            previewRows = result.first
            previewErrors = result.second
            showPreview = true
            status = if (result.second.isEmpty()) "Ready to import ${result.first.size} transaction(s)" else "${result.second.size} issue(s) found"
            statusIsError = result.second.isNotEmpty()
        }
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val filename = "spendingsaver-export_$timestamp.csv"
            scope.launch {
                val result = runCatching {
                    val csv = buildString {
                        appendLine("Date,Description,Amount,Account,Category")
                        transactions.forEach { tx ->
                            val account = accounts.find { it.id == tx.accountId }?.name ?: ""
                            appendLine("${tx.date},\"${tx.description}\",${tx.amount},\"$account\"")
                        }
                    }
                    // Atomic write: write to temp, then copy to destination
                    val tempFile = File(context.cacheDir, filename)
                    FileOutputStream(tempFile).use { it.write(csv.toByteArray()) }
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        tempFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    tempFile.delete()
                }
                if (result.isSuccess) {
                    status = "Exported ${transactions.size} transaction(s)"
                    statusIsError = false
                } else {
                    status = "Export failed: ${result.exceptionOrNull()?.message}"
                    statusIsError = true
                }
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SectionHeader("Import CSV")
        Spacer(Modifier.height(8.dp))
        AppCard(elevation = 2.dp) {
            Text(
                "Import transactions from a CSV with columns: Date, Description, Amount.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Optional columns: Account, Category",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ExposedDropdownMenuBox(expanded = accExpanded, onExpandedChange = { accExpanded = it }) {
                OutlinedTextField(
                    value = accounts.find { it.id == selectedAccountId }?.name ?: "Select account",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Import into account") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accExpanded) },
                )
                ExposedDropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                    if (accounts.isEmpty()) {
                        DropdownMenuItem(text = { Text("No accounts — add one first") }, onClick = { accExpanded = false })
                    }
                    accounts.forEach { account ->
                        DropdownMenuItem(text = { Text(account.name) }, onClick = { selectedAccountId = account.id; accExpanded = false })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                "Choose CSV file",
                enabled = selectedAccountId.isNotBlank(),
                icon = { Icon(Icons.Default.FileOpen, null) },
                onClick = { importPicker.launch(arrayOf("text/csv", "text/*")) },
            )
        }

        // Preview panel
        if (showPreview) {
            Spacer(Modifier.height(16.dp))
            AppCard(elevation = 2.dp) {
                Text(
                    "Preview (${previewRows.size} row(s))",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                if (previewErrors.isNotEmpty()) {
                    Text(
                        "Issues:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    previewErrors.take(5).forEach { err ->
                        Text(
                            "• $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (previewErrors.size > 5) {
                        Text(
                            "...and ${previewErrors.size - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                previewRows.take(10).forEach { tx ->
                    Text(
                        "${tx.date} | ${tx.description} | ${tx.amount}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (previewRows.size > 10) {
                    Text(
                        "...and ${previewRows.size - 10} more rows",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        "Import ${previewRows.size} transaction(s)",
                        enabled = previewRows.isNotEmpty() && !importing,
                        onClick = {
                            importing = true
                            scope.launch {
                                viewModel.importTransactionsFromCsv(selectedAccountId, previewRows)
                                status = "Imported ${previewRows.size} transaction(s)"
                                statusIsError = false
                                showPreview = false
                                previewRows = emptyList()
                                previewErrors = emptyList()
                                importing = false
                            }
                        },
                    )
                    TextButton(onClick = {
                        showPreview = false
                        previewRows = emptyList()
                        previewErrors = emptyList()
                        status = "Import cancelled"
                        statusIsError = false
                    }) {
                        Text("Cancel")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Export")
        Spacer(Modifier.height(8.dp))
        AppCard(elevation = 2.dp) {
            Text(
                "Export all transactions as CSV with Date, Description, Amount, Account, Category.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                "Export transactions",
                enabled = transactions.isNotEmpty(),
                icon = { Icon(Icons.Default.Download, null) },
                onClick = {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                    exportPicker.launch("spendingsaver-export_$timestamp.csv")
                },
            )
        }

        if (status != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                status!!,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
