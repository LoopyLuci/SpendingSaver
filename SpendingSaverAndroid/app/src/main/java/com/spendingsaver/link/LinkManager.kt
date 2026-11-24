package com.spendingsaver.link

import android.content.Context
import android.os.Build
import com.google.gson.JsonObject
import com.spendingsaver.sync.SharedPrefsSyncStateStore
import com.spendingsaver.sync.SyncException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The optional Transferd link ("connect from anywhere"). Lazy: the native node only starts when
 * the user pairs through it or a sync needs it, and it opens no listening port. Identity and
 * paired devices live in app-private `files/link` (excluded from backups); the relay list lives
 * in the sync preferences file, which is also excluded.
 */
@Singleton
class LinkManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(SharedPrefsSyncStateStore.FILE, Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var started = false

    val isAvailable: Boolean get() = NativeLink.available

    private fun relays(): List<String> =
        prefs.getString(K_RELAYS, "").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private suspend fun call(op: String, args: String = "{}"): JsonObject = withContext(Dispatchers.IO) {
        when (val r = LinkReply.parse(NativeLink.nativeCall(op, args))) {
            is LinkReply.Ok -> r.result
            is LinkReply.Error -> throw SyncException(r.message, SyncException.Kind.NETWORK)
        }
    }

    private suspend fun startLocked(relays: List<String>) = withContext(Dispatchers.IO) {
        if (!isAvailable) throw SyncException("Connect from anywhere is not included in this build", SyncException.Kind.PROTOCOL)
        val dir = File(context.filesDir, "link").apply { mkdirs() }
        when (val r = LinkReply.parse(NativeLink.nativeStart(dir.absolutePath, "Android ${Build.MODEL}", relays.joinToString(",")))) {
            is LinkReply.Ok -> started = true
            is LinkReply.Error -> throw SyncException(r.message, SyncException.Kind.NETWORK)
        }
    }

    private suspend fun ensureStarted() = lock.withLock { if (!started) startLocked(relays()) }

    /**
     * Verifies a desktop invite, pairs with that desktop, and makes sure this node is registered
     * on the relays the desktop uses. Returns the desktop's link id.
     */
    suspend fun acceptInvite(invite: String): String {
        ensureStarted()
        val reply = call("link.accept_invite", JsonObject().apply { addProperty("invite", invite.trim()) }.toString())
        val desktopRelays = reply.getAsJsonArray("relays")?.map { it.asString } ?: emptyList()
        val missing = desktopRelays.filterNot { it in relays() }
        if (missing.isNotEmpty()) {
            // A node registers only on the relays it started with: restart with the union. Pairing
            // is persisted by the node, so it survives the restart.
            val union = relays() + missing
            prefs.edit().putString(K_RELAYS, union.joinToString(",")).apply()
            lock.withLock {
                withContext(Dispatchers.IO) { NativeLink.nativeStop() }
                started = false
                startLocked(union)
            }
        }
        return reply.get("node_id").asString
    }

    /**
     * A local `http://127.0.0.1:port` that Transferd carries to the desktop's sync gateway. The
     * endpoint is stable for the life of the node and the connection is checked before it returns.
     */
    suspend fun resolve(desktopNodeId: String): String {
        ensureStarted()
        val reply = call("link.connect_hub", JsonObject().apply { addProperty("node_id", desktopNodeId) }.toString())
        return "http://" + reply.get("local_addr").asString
    }

    private companion object {
        const val K_RELAYS = "link_relays"
    }
}
