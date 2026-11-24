package com.spendingsaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.spendingsaver.repository.SettingsRepository
import com.spendingsaver.ui.theme.ACCENTS_PUBLIC
import com.spendingsaver.ui.viewmodel.MainViewModel
import com.spendingsaver.ui.components.*

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
        }
    }
}

@Composable
fun SettingsLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun ThemeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
}

@Composable
fun AccentDot(name: String, selected: Boolean, onClick: () -> Unit) {
    val color = com.spendingsaver.ui.theme.accentColor(name)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                    )
                    .clickable(onClick = onClick),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun optionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.labelMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsScreen(
    navController: NavHostController,
    settingsRepo: SettingsRepository,
    viewModel: com.spendingsaver.ui.viewmodel.MainViewModel = hiltViewModel(),
) {
    val settings by settingsRepo.settings.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.restoreBackup(uri) }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("Personalize the app's look and behavior", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        SettingsSection("Appearance") {
            SettingsLabel("Theme")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip("System", settings.themeMode == "system", Modifier.weight(1f)) { settingsRepo.setTheme("system") }
                ThemeChip("Light", settings.themeMode == "light", Modifier.weight(1f)) { settingsRepo.setTheme("light") }
                ThemeChip("Dark", settings.themeMode == "dark", Modifier.weight(1f)) { settingsRepo.setTheme("dark") }
            }
            Spacer(Modifier.height(16.dp))
            SettingsLabel("Accent color")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ACCENTS_PUBLIC.forEach { c ->
                    AccentDot(name = c, selected = settings.accentColor == c, onClick = { settingsRepo.setAccent(c) })
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsLabel("Text size")
            Slider(
            value = settings.textScale,
            onValueChange = { settingsRepo.setTextScale(it) },
            valueRange = 0.85f..1.3f,
            steps = 8,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("A", style = MaterialTheme.typography.bodySmall)
            Text("A", style = MaterialTheme.typography.headlineMedium)
            }
            }

            SettingsSection("Privacy & display") {
            SwitchRow("Show balances", "Hide amounts for privacy when off", settings.showBalance) { settingsRepo.setShowBalance(it) }
            SwitchRow("Compact mode", "Denser rows in lists", settings.compactMode) { settingsRepo.setCompact(it) }
        }

        SettingsSection("Currency & dates") {
            var currency by remember(settings.defaultCurrency) { mutableStateOf(settings.defaultCurrency) }
            var format by remember(settings.dateFormat) { mutableStateOf(settings.dateFormat) }
            val formatOk = remember(format) {
                format.isNotBlank() && runCatching { java.text.SimpleDateFormat(format, java.util.Locale.US).format(java.util.Date()) }.isSuccess
            }
            ComboField(
                label = "Default currency (pick one or type its 3-letter code)",
                value = currency,
                onValueChange = {
                    currency = it.uppercase().take(3)
                    if (currency.length == 3) settingsRepo.setCurrency(currency)
                },
                suggestions = Choices.CURRENCIES,
                isError = currency.length != 3,
                supportingText = if (currency.length != 3) "Three letters, e.g. USD" else null,
            )
            Spacer(Modifier.height(12.dp))
            ComboField(
                label = "Date format (pick one or type a pattern)",
                value = format,
                onValueChange = {
                    format = it
                    if (it.isNotBlank() && runCatching { java.text.SimpleDateFormat(it, java.util.Locale.US).format(java.util.Date()) }.isSuccess) settingsRepo.setDateFormat(it)
                },
                suggestions = Choices.DATE_FORMATS,
                isError = !formatOk,
                supportingText = if (formatOk) "Example: " + java.text.SimpleDateFormat(format, java.util.Locale.US).format(java.util.Date()) else "Not a valid pattern (try yyyy-MM-dd)",
            )
        }

        SettingsSection("Data & backups") {
            Text(
                "Export a personal JSON backup of all your accounts, transactions, categories and budgets, or restore a previous backup. Everything stays on this device — no server needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { exportLauncher.launch("spendingsaver-backup.json") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export backup")
                }
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Restore backup")
                }
            }
            backupStatus?.let { status ->
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.startsWith("Backup")) colorScheme.primary else colorScheme.error,
                )
            }
        }

        SyncSection()

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { settingsRepo.reset() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Reset all settings")
        }
        Spacer(Modifier.height(24.dp))
    }
}
