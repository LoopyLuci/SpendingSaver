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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.spendingsaver.repository.SettingsRepository
import com.spendingsaver.ui.theme.ACCENTS_PUBLIC
import com.spendingsaver.ui.viewmodel.MainViewModel

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
            SettingsLabel("Default currency")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("USD", "EUR", "GBP", "CAD", "AUD", "JPY", "INR", "BRL").forEach { c ->
                    optionChip(c, settings.defaultCurrency == c) { settingsRepo.setCurrency(c) }
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsLabel("Date format")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy").forEach { d ->
                    optionChip(d, settings.dateFormat == d) { settingsRepo.setDateFormat(d) }
                }
            }
        }

        SettingsSection("Pair with server") {
            Text(
                if (settings.syncHost.isBlank())
                    "No server connected. Pair this phone with a SpendingSaver server to sync over your local network."
                else
                    "Connected to ${settings.syncHost.trim('/').removePrefix("http://").removePrefix("https://")}",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { navController.navigate("connect") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Cast, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (settings.syncHost.isBlank()) "Pair with server" else "Manage connection")
            }
        }

        SettingsSection("Sync & data") {
            SwitchRow("Automatic sync", "Sync with connected services", settings.syncEnabled) { settingsRepo.setSyncEnabled(it) }
            if (settings.syncEnabled) {
                Spacer(Modifier.height(12.dp))
                SettingsLabel("Sync interval (minutes)")
                Slider(
                    value = settings.syncIntervalMinutes.toFloat(),
                    onValueChange = { settingsRepo.setSyncInterval(it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 4,
                )
                Text("Every ${settings.syncIntervalMinutes} min", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.syncNow() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sync now")
            }
        }

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
