package com.spendingsaver.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.spendingsaver.repository.SettingsRepository
import com.spendingsaver.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    settingsRepo: SettingsRepository,
) {
    val settings by settingsRepo.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(false) }

    var myKey by remember { mutableStateOf<String?>(null) }
    var myKeyError by remember { mutableStateOf<String?>(null) }
    var fetchingKey by remember { mutableStateOf(false) }

    val currentSyncHost = settings.syncHost
    val colorScheme = MaterialTheme.colorScheme
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    )

    fun buildBase(hostInput: String): String {
        val trimmed = hostInput.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed.trimEnd('/')
        } else if (trimmed.isBlank()) {
            ""
        } else {
            "http://$trimmed"
        }
    }

    fun pairAndConnect() {
        val base = buildBase(host)
        if (base.isBlank()) {
            status = "Enter a server host"
            statusIsError = true
            return
        }
        if (key.trim().isBlank()) {
            status = "Enter the 6-character connection key"
            statusIsError = true
            return
        }
        val enteredKey = key.trim()
        val deviceId = UUID.randomUUID().toString()
        val deviceName = Build.MODEL
        busy = true
        status = "Contacting ${base.trim('/')}..."
        statusIsError = false
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val infoCode = httpGetCode("$base/api/host/info")
                if (infoCode !in 200..299) {
                    throw IllegalStateException("Host did not respond (HTTP $infoCode). Check the address.")
                }
                val body = "{\"key\":\"$enteredKey\",\"device_id\":\"$deviceId\",\"name\":\"$deviceName\"}"
                val pairCode = httpPost("$base/api/host/pair", body)
                if (pairCode == 401) {
                    throw IllegalStateException("Wrong connection key. Check the 6 characters and try again.")
                }
                if (pairCode !in 200..299) {
                    throw IllegalStateException("Pairing failed (HTTP $pairCode).")
                }
                base
            }
            withContext(Dispatchers.Main) {
                busy = false
                result.onSuccess { serverBase ->
                    settingsRepo.setSyncHost(serverBase)
                    paired = true
                    status = "Paired and connected to ${serverBase.trim('/')}"
                    statusIsError = false
                }.onFailure { e ->
                    paired = false
                    status = e.message ?: "Pairing failed"
                    statusIsError = true
                }
            }
        }
    }

    fun fetchMyKey() {
        val base = settings.syncHost
        if (base.isBlank()) {
            myKeyError = "No server is configured yet."
            myKey = null
            return
        }
        fetchingKey = true
        myKeyError = null
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val body = httpGet("$base/api/host/info")
                    ?: throw IllegalStateException("Could not reach $base")
                val root = JsonParser.parseString(body).asJsonObject
                root.get("key")?.asString
                    ?: throw IllegalStateException("Server did not return a key.")
            }
            withContext(Dispatchers.Main) {
                fetchingKey = false
                result.onSuccess { k ->
                    myKey = k
                    myKeyError = null
                }.onFailure { e ->
                    myKey = null
                    myKeyError = e.message ?: "Could not fetch key"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connect", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(
            "Pair this phone with a SpendingSaver server over your local network to sync your data.",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )

        // ---- Pair & Connect ----
        AppCard(elevation = 2.dp) {
            Text("Pair & connect", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Server host (e.g. 192.168.1.5:8125)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Connection key (6 hex chars)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { pairAndConnect() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Icon(Icons.Default.Cast, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Connecting..." else "Pair and Connect")
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statusIsError) colorScheme.error else colorScheme.primary,
                )
            }
            if (paired) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Connected to ${settings.syncHost}", style = MaterialTheme.typography.bodySmall, color = colorScheme.primary)
                }
            }
        }

        // ---- Show my connection key ----
        AppCard(elevation = 2.dp) {
            Text("Show my connection key", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (currentSyncHost.isBlank())
                    "Pair this phone with a server first, then share its key with a second phone."
                else
                    "Connected to ${currentSyncHost.trim('/')}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { fetchMyKey() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !fetchingKey && currentSyncHost.isNotBlank(),
            ) {
                Text(if (fetchingKey) "Fetching..." else "Show my connection key")
            }
            val keyValue = myKey
            if (keyValue != null) {
                Spacer(Modifier.height(12.dp))
                Text("Connection key", style = MaterialTheme.typography.labelLarge, color = colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    keyValue,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Give this key to the other phone to pair it with your server.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val err = myKeyError
            if (err != null) {
                Spacer(Modifier.height(8.dp))
                Text(err, style = MaterialTheme.typography.bodyMedium, color = colorScheme.error)
            }
        }
    }
}

// ---- HTTP helpers (plain HttpURLConnection, matching SyncWorker) ----

private fun httpGetCode(url: String): Int {
    val conn = URL(url).openConnection() as HttpURLConnection
    return try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.responseCode
    } finally {
        conn.disconnect()
    }
}

private fun httpPost(url: String, jsonBody: String): Int {
    val conn = URL(url).openConnection() as HttpURLConnection
    return try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        conn.responseCode
    } finally {
        conn.disconnect()
    }
}

private fun httpGet(url: String): String? {
    val conn = URL(url).openConnection() as HttpURLConnection
    return try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        if (conn.responseCode !in 200..299) null
        else conn.inputStream.reader().use { it.readText() }
    } catch (e: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}
