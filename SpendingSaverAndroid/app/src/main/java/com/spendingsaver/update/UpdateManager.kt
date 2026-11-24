package com.spendingsaver.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.spendingsaver.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val release: ReleaseInfo) : UpdateState()
    data class Downloading(val release: ReleaseInfo, val done: Long, val total: Long) : UpdateState()
    data class Downloaded(val release: ReleaseInfo, val file: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/**
 * Looks for a newer APK in the project's GitHub releases, downloads it, checks it, and hands it to Android's installer.
 * Nothing here runs unless the user turned automatic updates on or tapped a button. Android requires the user to confirm
 * every install of an app that is not a device owner, so "install" always ends at the system confirmation screen.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) {
    private val prefs = context.getSharedPreferences("spendingsaver_update", Context.MODE_PRIVATE)

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val currentCode: Int get() = BuildConfig.VERSION_CODE

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _mode = MutableStateFlow(modeFromPrefs())
    val mode: StateFlow<UpdateMode> = _mode.asStateFlow()
    private val _repo = MutableStateFlow(prefs.getString(KEY_REPO, DEFAULT_REPO) ?: DEFAULT_REPO)
    val repo: StateFlow<String> = _repo.asStateFlow()
    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()
    private val _lastChecked = MutableStateFlow(prefs.getLong(KEY_LAST, 0L))
    val lastChecked: StateFlow<Long> = _lastChecked.asStateFlow()
    /** Set when an automatic install prompt should be shown once for this release. */
    private val _prompt = MutableStateFlow<UpdateState.Downloaded?>(null)
    val prompt: StateFlow<UpdateState.Downloaded?> = _prompt.asStateFlow()

    val hasToken: Boolean get() = !prefs.getString(KEY_TOKEN, null).isNullOrBlank()

    init { restoreDownloaded() }

    private fun modeFromPrefs() = UpdateMode.values().firstOrNull { it.name == prefs.getString(KEY_MODE, "") } ?: UpdateMode.MANUAL

    fun setMode(m: UpdateMode) { _mode.value = m; prefs.edit().putString(KEY_MODE, m.name).apply() }
    fun setWifiOnly(v: Boolean) { _wifiOnly.value = v; prefs.edit().putBoolean(KEY_WIFI_ONLY, v).apply() }
    fun setRepo(r: String): Boolean {
        val t = r.trim()
        if (!UpdateLogic.isValidRepo(t)) return false
        _repo.value = t; prefs.edit().putString(KEY_REPO, t).apply(); return true
    }
    /** An optional read-only GitHub token, only needed while releases live in a private repository. */
    fun setToken(t: String) { prefs.edit().putString(KEY_TOKEN, t.trim().ifBlank { null }).apply() }

    private fun token() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    private fun updatesDir() = File(context.cacheDir, "updates").apply { mkdirs() }

    private fun isMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.isActiveNetworkMetered
    }

    private fun feedRequest(): Request {
        val b = Request.Builder().url("https://api.github.com/repos/${_repo.value}/releases/latest")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "SpendingSaver-Android/$currentVersion")
        token()?.let { b.header("Authorization", "Bearer $it") }
        return b.build()
    }

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Checking
        val result: UpdateState = try {
            http.newCall(feedRequest()).execute().use { resp ->
                when {
                    resp.code == 404 -> UpdateState.Failed(
                        "No release found. If the project's repository is private, add a read-only access token below, or publish releases in a public repository."
                    )
                    resp.code == 401 || resp.code == 403 -> UpdateState.Failed("GitHub refused the request (${resp.code}). Check the access token, or try again later.")
                    !resp.isSuccessful -> UpdateState.Failed("The update server answered ${resp.code}.")
                    else -> {
                        val rel = UpdateLogic.parseRelease(resp.body?.string().orEmpty(), com.spendingsaver.BuildConfig.DEVICE_FLAVOR)
                        when {
                            rel == null -> UpdateState.Failed("The latest release has no APK attached.")
                            UpdateLogic.isNewer(rel.version, currentVersion) -> existingDownload(rel) ?: UpdateState.Available(rel)
                            else -> UpdateState.UpToDate
                        }
                    }
                }
            }
        } catch (e: Exception) {
            UpdateState.Failed("Could not reach the update server: ${e.message ?: e.javaClass.simpleName}")
        }
        _lastChecked.value = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST, _lastChecked.value).apply()
        if (result is UpdateState.Available) cacheRelease(result.release)
        _state.value = result
        result
    }

    /** Downloads and verifies the APK. [auto] downloads obey the Wi-Fi-only setting. */
    suspend fun download(release: ReleaseInfo, auto: Boolean = false): UpdateState = withContext(Dispatchers.IO) {
        if (auto && _wifiOnly.value && isMetered()) {
            return@withContext _state.value
        }
        val target = File(updatesDir(), release.apkName)
        val part = File(updatesDir(), release.apkName + ".part")
        part.delete()
        val result: UpdateState = try {
            val useApi = token() != null && release.apiUrl != null
            val b = Request.Builder().url(if (useApi) release.apiUrl!! else release.downloadUrl)
                .header("User-Agent", "SpendingSaver-Android/$currentVersion")
            if (useApi) { b.header("Accept", "application/octet-stream"); b.header("Authorization", "Bearer ${token()}") }
            http.newCall(b.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use UpdateState.Failed("Download failed (${resp.code}).")
                if (!UpdateLogic.isAllowedUrl(resp.request.url.toString())) return@use UpdateState.Failed("The download was redirected to an untrusted address and was stopped.")
                val body = resp.body ?: return@use UpdateState.Failed("The download was empty.")
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                if (total > MAX_BYTES) return@use UpdateState.Failed("The file is larger than the ${MAX_BYTES / 1_000_000} MB limit.")
                val tooBig = "The file is larger than the ${MAX_BYTES / 1_000_000} MB limit."
                var done = 0L
                var lastEmit = 0L
                var overLimit = false
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (!overLimit) {
                            val n = input.read(buf)
                            if (n < 0) break
                            done += n
                            if (done > MAX_BYTES) { overLimit = true; break }
                            out.write(buf, 0, n)
                            val now = System.nanoTime()
                            if (now - lastEmit > 150_000_000L) { lastEmit = now; _state.value = UpdateState.Downloading(release, done, total) }
                        }
                    }
                }
                if (overLimit) { part.delete(); return@use UpdateState.Failed(tooBig) }
                verifyAndFinish(release, part, target)
            }
        } catch (e: Exception) {
            part.delete()
            UpdateState.Failed("Download failed: ${e.message ?: e.javaClass.simpleName}")
        }
        _state.value = result
        if (result is UpdateState.Downloaded && auto && _mode.value == UpdateMode.DOWNLOAD_INSTALL) _prompt.value = result
        result
    }

    private fun verifyAndFinish(release: ReleaseInfo, part: File, target: File): UpdateState {
        release.sha256?.let { expected ->
            if (UpdateLogic.sha256(part) != expected) { part.delete(); return UpdateState.Failed("The downloaded file does not match its published checksum, so it was discarded.") }
        }
        val info = context.packageManager.getPackageArchiveInfo(part.absolutePath, 0)
        if (info == null) { part.delete(); return UpdateState.Failed("The downloaded file is not a valid app package.") }
        if (info.packageName != context.packageName) { part.delete(); return UpdateState.Failed("The package is for a different app (${info.packageName}).") }
        @Suppress("DEPRECATION")
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        if (code <= currentCode) { part.delete(); return UpdateState.Failed("That package is not newer than the installed version.") }
        target.delete()
        if (!part.renameTo(target)) { part.delete(); return UpdateState.Failed("Could not save the downloaded file.") }
        return UpdateState.Downloaded(release, target)
    }

    /** Called once on app start. Does nothing in manual mode. */
    suspend fun autoRun() {
        if (_mode.value == UpdateMode.MANUAL) return
        val due = System.currentTimeMillis() - _lastChecked.value > CHECK_EVERY_MS
        var s = _state.value
        if (s is UpdateState.Idle && due) s = check()
        if (s is UpdateState.Available) download(s.release, auto = true)
        else if (s is UpdateState.Downloaded && _mode.value == UpdateMode.DOWNLOAD_INSTALL) _prompt.value = s
    }

    fun dismissPrompt() { _prompt.value = null }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Settings page where the user allows this app to install packages. */
    fun permissionIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Opens Android's own install confirmation for a downloaded update. */
    fun installIntent(file: File): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(file), APK_MIME)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    fun shareIntent(file: File, label: String): Intent {
        val send = Intent(Intent.ACTION_SEND).setType(APK_MIME)
            .putExtra(Intent.EXTRA_STREAM, uriFor(file)).putExtra(Intent.EXTRA_SUBJECT, label)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share SpendingSaver APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** A copy of the APK this app is running from, in a place Android can share. */
    suspend fun ownApk(): File? = withContext(Dispatchers.IO) {
        val src = File(context.applicationInfo.sourceDir)
        if (!src.isFile) return@withContext null
        val splits = context.applicationInfo.splitSourceDirs
        if (!splits.isNullOrEmpty()) return@withContext null // split installs cannot be shared as one file
        File(updatesDir(), "SpendingSaver-$currentVersion.apk").also { src.copyTo(it, overwrite = true) }
    }

    /** Copies [file] to the place the user picked in the system file chooser. */
    suspend fun copyTo(file: File, dest: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(dest, "w")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            true
        }.getOrDefault(false)
    }

    fun deleteDownload() {
        updatesDir().listFiles()?.forEach { it.delete() }
        prefs.edit().remove(KEY_RELEASE).apply()
        _state.value = UpdateState.Idle
        _prompt.value = null
    }

    // ---- remembering the last found release so "available version" survives a restart ----

    private fun cacheRelease(r: ReleaseInfo) { prefs.edit().putString(KEY_RELEASE, com.google.gson.Gson().toJson(r)).apply() }

    private fun existingDownload(r: ReleaseInfo): UpdateState? {
        val f = File(updatesDir(), r.apkName)
        return if (f.isFile && (r.sha256 == null || runCatching { UpdateLogic.sha256(f) == r.sha256 }.getOrDefault(false))) UpdateState.Downloaded(r, f) else null
    }

    private fun restoreDownloaded() {
        val rel = prefs.getString(KEY_RELEASE, null)?.let { runCatching { com.google.gson.Gson().fromJson(it, ReleaseInfo::class.java) }.getOrNull() } ?: return
        if (!UpdateLogic.isNewer(rel.version, currentVersion)) { prefs.edit().remove(KEY_RELEASE).apply(); return }
        _state.value = existingDownload(rel) ?: UpdateState.Available(rel)
    }

    companion object {
        const val DEFAULT_REPO = "LoopyLuci/SpendingSaver"
        const val APK_MIME = "application/vnd.android.package-archive"
        private const val MAX_BYTES = 200_000_000L
        private const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000
        private const val KEY_MODE = "mode"
        private const val KEY_REPO = "repo"
        private const val KEY_TOKEN = "token"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_LAST = "last_checked"
        private const val KEY_RELEASE = "release"
    }
}
