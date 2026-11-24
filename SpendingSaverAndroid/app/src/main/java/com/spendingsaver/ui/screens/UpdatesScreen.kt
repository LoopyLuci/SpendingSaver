package com.spendingsaver.ui.screens

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.spendingsaver.ui.components.*
import com.spendingsaver.update.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(val manager: UpdateManager) : ViewModel()

private fun size(bytes: Long) = if (bytes <= 0) "unknown size" else "%.1f MB".format(bytes / 1_000_000.0)

/**
 * Asks before installing. If Android has not yet allowed this app to install packages, it explains and opens that
 * setting first. The final confirmation is always Android's own installer screen.
 */
@Composable
fun InstallDialog(manager: UpdateManager, release: ReleaseInfo, file: File, onDone: () -> Unit) {
    val context = LocalContext.current
    if (!manager.canInstall()) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Allow installing updates") },
            text = { Text("Android needs your permission before SpendingSaver can install an update. Turn on \"Allow from this source\" on the next screen, then come back and tap Install again.") },
            confirmButton = { TextButton(onClick = { runCatching { context.startActivity(manager.permissionIntent()) }; onDone() }) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = onDone) { Text("Not now") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Install version ${release.version}?") },
            text = { Text("You have ${manager.currentVersion}. Your accounts and transactions stay on this device. Android will ask you to confirm, and the app restarts afterwards.") },
            confirmButton = {
                TextButton(onClick = {
                    try { context.startActivity(manager.installIntent(file)) }
                    catch (e: ActivityNotFoundException) { Toast.makeText(context, "No installer available on this device.", Toast.LENGTH_LONG).show() }
                    onDone()
                }) { Text("Install") }
            },
            dismissButton = { TextButton(onClick = onDone) { Text("Later") } },
        )
    }
}

@Composable
fun UpdatesScreen(vm: UpdatesViewModel = hiltViewModel()) {
    val m = vm.manager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by m.state.collectAsState()
    val mode by m.mode.collectAsState()
    val repo by m.repo.collectAsState()
    val wifiOnly by m.wifiOnly.collectAsState()
    val lastChecked by m.lastChecked.collectAsState()

    var installFor by remember { mutableStateOf<UpdateState.Downloaded?>(null) }
    var pendingSave by remember { mutableStateOf<File?>(null) }
    var repoText by remember(repo) { mutableStateOf(repo) }
    var tokenText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(UpdateManager.APK_MIME)) { uri ->
        val f = pendingSave
        if (uri != null && f != null) scope.launch {
            message = if (m.copyTo(f, uri)) "APK saved." else "Could not save the APK there."
        }
        pendingSave = null
    }

    fun share(file: File?, label: String) {
        if (file == null) { message = "This install cannot be shared as a single file."; return }
        try { context.startActivity(m.shareIntent(file, label)) } catch (e: Exception) { message = "Nothing to share with on this device." }
    }

    val available: ReleaseInfo? = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Downloaded -> s.release
        else -> null
    }
    val downloaded = state as? UpdateState.Downloaded

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeroCard("Installed version", m.currentVersion, "Build ${m.currentCode}") }
        item {
            AppCard {
                Row2("Current version", "${m.currentVersion} (build ${m.currentCode})")
                Row2(
                    "Available version",
                    when {
                        available != null -> available.version
                        state is UpdateState.UpToDate -> "None, you are up to date"
                        state is UpdateState.Checking -> "Checking…"
                        else -> "Not checked yet"
                    },
                )
                Row2("Last checked", if (lastChecked == 0L) "Never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastChecked)))
                Row2("Update mode", mode.label)
            }
        }

        item {
            when (val s = state) {
                is UpdateState.Checking -> AppCard { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text("Checking for updates…") } }
                is UpdateState.UpToDate -> AppCard { Text("You have the latest version.", fontWeight = FontWeight.Medium) }
                is UpdateState.Failed -> AppCard(containerColor = MaterialTheme.colorScheme.errorContainer) { Text(s.message, color = MaterialTheme.colorScheme.onErrorContainer) }
                is UpdateState.Available -> AppCard {
                    Text("Version ${s.release.version} is available", fontWeight = FontWeight.SemiBold)
                    Text("${size(s.release.apkSize)}${if (s.release.sha256 != null) " · checksum verified after download" else " · no checksum published"}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { scope.launch { m.download(s.release) } }, modifier = Modifier.fillMaxWidth()) { Text("Download update") }
                }
                is UpdateState.Downloading -> AppCard {
                    Text("Downloading version ${s.release.version}…", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (s.total > 0) LinearProgressIndicator(progress = (s.done.toFloat() / s.total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${size(s.done)} of ${size(s.total)}", style = MaterialTheme.typography.bodySmall)
                }
                is UpdateState.Downloaded -> AppCard {
                    Text("Version ${s.release.version} is downloaded and checked", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { installFor = s }, modifier = Modifier.fillMaxWidth()) { Text("Install update") }
                }
                UpdateState.Idle -> {}
            }
        }
        item {
            OutlinedButton(
                onClick = { scope.launch { m.check() } },
                enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Check for updates") }
        }
        if (available != null && available.notes.isNotBlank()) {
            item { SectionHeader("What's new in ${available.version}") }
            item { AppCard { Text(available.notes.take(2000), style = MaterialTheme.typography.bodyMedium) } }
        }

        item { SectionHeader("Save or share the APK") }
        item {
            Text("Keep a copy of the app file, or send it to another phone.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val f = m.ownApk()
                        if (f == null) message = "This install cannot be saved as a single file."
                        else { pendingSave = f; saveLauncher.launch("SpendingSaver-${m.currentVersion}.apk") }
                    }
                }, modifier = Modifier.weight(1f)) { Text("Save this app") }
                OutlinedButton(onClick = { scope.launch { share(m.ownApk(), "SpendingSaver ${m.currentVersion}") } }, modifier = Modifier.weight(1f)) { Text("Share this app") }
            }
        }
        if (downloaded != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pendingSave = downloaded.file; saveLauncher.launch(downloaded.release.apkName) }, modifier = Modifier.weight(1f)) { Text("Save update") }
                    OutlinedButton(onClick = { share(downloaded.file, "SpendingSaver ${downloaded.release.version}") }, modifier = Modifier.weight(1f)) { Text("Share update") }
                }
            }
            item { TextButton(onClick = { m.deleteDownload() }) { Text("Delete downloaded update") } }
        }
        message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) } }

        item { SectionHeader("How updates work") }
        items(UpdateMode.values().size) { i ->
            val o = UpdateMode.values()[i]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                RadioButton(selected = mode == o, onClick = { m.setMode(o) })
                Column(Modifier.padding(top = 12.dp)) {
                    Text(o.label, fontWeight = FontWeight.Medium)
                    Text(o.help, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (mode != UpdateMode.MANUAL) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Download on Wi-Fi only", fontWeight = FontWeight.Medium)
                    Text("Automatic downloads wait for an unmetered connection.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = wifiOnly, onCheckedChange = m::setWifiOnly)
            }
        }
        item { SectionHeader("Update source") }
        item {
            ComboField(
                "GitHub repository", repoText, { repoText = it; if (UpdateLogic.isValidRepo(it)) m.setRepo(it) },
                suggestions = listOf(UpdateManager.DEFAULT_REPO),
                isError = !UpdateLogic.isValidRepo(repoText), supportingText = "owner/name. Releases are read over HTTPS from github.com only.",
            )
        }
        item {
            OutlinedTextField(
                value = tokenText, onValueChange = { tokenText = it; m.setToken(it) },
                label = { Text(if (m.hasToken && tokenText.isEmpty()) "Access token (saved)" else "Access token (optional)") },
                supportingText = { Text("Only needed while releases are in a private repository. Use a read-only token; it is kept on this device, left out of backups, and sent only to github.com.") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                "Checking contacts GitHub with a plain request (no account data or analytics). Every update is checked against its published checksum " +
                    "and must be the same app and newer. Android always shows its own confirmation before installing; an app cannot install silently.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    installFor?.let { InstallDialog(m, it.release, it.file) { installFor = null } }
}

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
