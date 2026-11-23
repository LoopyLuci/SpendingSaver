package com.spendingsaver.sync

import java.net.URI

/**
 * Validation for the user-entered sync hub address.
 *
 * Plain `http://` is only accepted for local-network hosts, because the device
 * token and finance data would otherwise cross the internet unencrypted.
 */
object HubUrl {
    sealed class Result {
        data class Ok(val url: String) : Result()
        data class Invalid(val reason: String) : Result()
    }

    fun parse(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.Invalid("Enter the hub address, e.g. 192.168.1.20:8125")
        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return Result.Invalid("That is not a valid address")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return Result.Invalid("Use an http:// or https:// address")
        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty()) return Result.Invalid("That is not a valid address")
        if (uri.userInfo != null) return Result.Invalid("Do not put credentials in the address")
        if (scheme == "http" && !isLocalHost(host)) {
            return Result.Invalid("Use https:// for a hub outside your local network")
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val shownHost = if (host.contains(':')) "[$host]" else host
        return Result.Ok("$scheme://$shownHost$port")
    }

    /** Loopback, RFC 1918, link-local, `.local`/`.lan` names and single-label hostnames. */
    fun isLocalHost(rawHost: String): Boolean {
        val host = rawHost.removePrefix("[").removeSuffix("]").lowercase()
        if (host == "localhost" || host == "::1") return true
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) return true
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return a == 127 || a == 10 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254)
        }
        if (host.startsWith("fe80:") || host.startsWith("fd") || host.startsWith("fc")) return host.contains(':')
        return !host.contains('.') && !host.contains(':')
    }
}
