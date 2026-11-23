package com.spendingsaver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingsaver.ui.viewmodel.SyncViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Optional pairing with the SpendingSaver desktop hub. The app works fully
 * without it; nothing is sent anywhere until the user pairs and taps Sync.
 */
@Composable
fun SyncSection(viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme

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
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Hub address") },
                placeholder = { Text("192.168.1.20:8125") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.uppercase() },
                label = { Text("Pairing key") },
                supportingText = { Text("Shown on the desktop in the hub's host info (local access only)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.pair(url, key) },
                enabled = !state.busy && url.isNotBlank() && key.isNotBlank(),
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
