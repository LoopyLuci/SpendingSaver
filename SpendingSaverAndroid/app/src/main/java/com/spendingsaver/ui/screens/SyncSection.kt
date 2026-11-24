package com.spendingsaver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingsaver.ui.viewmodel.SyncViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.DateFormat
import java.util.Date

/**
 * Optional pairing with the SpendingSaver desktop hub. The app works fully
 * without it; nothing is sent anywhere until the user pairs and taps Sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSection(viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var anywhere by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        // A scanned code carries the invite and the pairing key, so one scan pairs and syncs.
        result.contents?.let { text ->
            // The native desktop app shows a same-Wi-Fi code: sslan:<address>:<port>:<key>.
            if (text.startsWith("sslan:")) {
                val body = text.removePrefix("sslan:")
                val cut = body.lastIndexOf(':')
                if (cut > 0) { url = body.substring(0, cut); key = body.substring(cut + 1); viewModel.pair(url, key) }
            } else { invite = text; viewModel.pairViaLink(text, key) }
        }
    }

    SettingsSection("Desktop sync (optional)") {
        Text(
            "Sync accounts, categories, transactions and budgets with the SpendingSaver desktop app on your " +
                "network. Both apps keep working on their own; unpairing never deletes data on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (state.paired) {
            Text("Paired with ${state.hubUrl}", style = MaterialTheme.typography.bodyLarge)
            Text(
                state.lastSyncAtMs?.let { "Last synced " + DateFormat.getDateTimeInstance().format(Date(it)) }
                    ?: "Not synced yet",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Sync automatically", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "About once an hour when online. Off by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.autoSync, onCheckedChange = viewModel::setAutoSync, enabled = !state.busy)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = viewModel::syncNow, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Text(if (state.busy) "Syncing…" else "Sync now")
                }
                OutlinedButton(onClick = viewModel::unpair, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Text("Unpair")
                }
            }
        } else {
            // The fastest way to pair is always on top: one scan pairs and syncs.
            Button(
                onClick = {
                    scanner.launch(
                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the QR code on your desktop")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false),
                    )
                },
                enabled = !state.busy && viewModel.linkAvailable,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan QR code to pair")
            }
            Text(
                if (viewModel.linkAvailable) "On the desktop: Phone Sync → Connect from anywhere → New phone invite."
                else "QR pairing needs the link component, which is missing from this build. Use manual pairing below.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Text("Or pair manually", style = MaterialTheme.typography.labelLarge, color = colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            if (viewModel.linkAvailable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !anywhere, onClick = { anywhere = false }, label = { Text("Same network") })
                    FilterChip(selected = anywhere, onClick = { anywhere = true }, label = { Text("From anywhere") })
                }
                Spacer(Modifier.height(8.dp))
            }
            if (anywhere) {
                OutlinedTextField(
                    value = invite,
                    onValueChange = { invite = it.trim() },
                    label = { Text("Paste the code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Hub address") },
                    placeholder = { Text("192.168.1.20:8125") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val needsKey = !anywhere || (invite.isNotBlank() && !invite.startsWith("ss1:"))
            if (needsKey) Spacer(Modifier.height(8.dp))
            if (needsKey) OutlinedTextField(
                value = key,
                onValueChange = { key = it.uppercase() },
                label = { Text("Pairing key") },
                supportingText = { Text("Shown on the desktop in Phone Sync (local access only)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { if (anywhere) viewModel.pairViaLink(invite, key) else viewModel.pair(url, key) },
                enabled = !state.busy && (if (anywhere) invite.isNotBlank() && (!needsKey || key.isNotBlank()) else url.isNotBlank() && key.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.busy) "Pairing…" else "Pair and sync") }
        }

        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isError) colorScheme.error else colorScheme.primary,
            )
        }
    }
}
