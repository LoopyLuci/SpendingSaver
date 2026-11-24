package com.spendingsaver.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

/** How the app treats updates. Manual is the default: nothing is contacted until the user asks. */
enum class UpdateMode(val label: String, val help: String) {
    MANUAL("Manual", "Check and download only when you tap the buttons. Nothing runs in the background."),
    DOWNLOAD("Download automatically", "Checks when the app opens and downloads new versions. You choose when to install."),
    DOWNLOAD_INSTALL("Download and offer to install", "Checks and downloads, then asks you to install straight away. Android always needs your confirmation."),
}

/** A published version, read from a GitHub release. */
data class ReleaseInfo(
    val version: String,
    val tag: String,
    val notes: String,
    val apkName: String,
    val apkSize: Long,
    val downloadUrl: String,
    /** API asset URL; needed to download from a private repository with a token. */
    val apiUrl: String?,
    /** Lower-case hex SHA-256 published by GitHub for the asset, when present. */
    val sha256: String?,
    val publishedAt: String?,
)

object UpdateLogic {
    /** Hosts a release listing or APK may come from. Anything else is refused, including redirects. */
    private val ALLOWED_HOSTS = setOf(
        "api.github.com", "github.com",
        "objects.githubusercontent.com", "release-assets.githubusercontent.com", "github-releases.githubusercontent.com",
    )

    fun isAllowedUrl(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.host?.lowercase() in ALLOWED_HOSTS
    }.getOrDefault(false)

    fun isValidRepo(repo: String) = Regex("^[A-Za-z0-9_.-]{1,39}/[A-Za-z0-9_.-]{1,100}$").matches(repo.trim())

    /** "v1.2.3-beta" -> [1, 2, 3]; null when there is no number. */
    fun parseVersion(raw: String): List<Int>? {
        val core = raw.trim().removePrefix("v").removePrefix("V").takeWhile { it.isDigit() || it == '.' }
        val parts = core.split('.').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
        return parts.ifEmpty { null }
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate) ?: return false
        val b = parseVersion(current) ?: return true
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Reads the GitHub "latest release" JSON. Returns null when it has no APK asset or no usable version. */
    fun parseRelease(json: String): ReleaseInfo? {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        if (root.bool("draft") || root.bool("prerelease")) return null
        val tag = root.str("tag_name") ?: return null
        if (parseVersion(tag) == null) return null
        val assets = root.getAsJsonArray("assets") ?: return null
        val apk = assets.map { it.asJsonObject }
            .filter { (it.str("name") ?: "").endsWith(".apk", ignoreCase = true) }
            .minByOrNull { if ((it.str("name") ?: "").contains("debug", true)) 1 else 0 } ?: return null
        val url = apk.str("browser_download_url") ?: return null
        if (!isAllowedUrl(url)) return null
        val digest = apk.str("digest")?.removePrefix("sha256:")?.lowercase()?.takeIf { Regex("^[0-9a-f]{64}$").matches(it) }
        return ReleaseInfo(
            version = tag.removePrefix("v").removePrefix("V"),
            tag = tag,
            notes = (root.str("body") ?: "").trim(),
            apkName = (apk.str("name") ?: "SpendingSaver.apk").replace(Regex("[^A-Za-z0-9._-]"), "_"),
            apkSize = apk.get("size")?.takeIf { it.isJsonPrimitive }?.asLong ?: -1L,
            downloadUrl = url,
            apiUrl = apk.str("url")?.takeIf(::isAllowedUrl),
            sha256 = digest,
            publishedAt = root.str("published_at"),
        )
    }

    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = file.inputStream().use(::sha256)

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
}
