package com.spendingsaver.sync

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.spendingsaver.data.AppDatabase
import com.spendingsaver.link.LinkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing wrapper around [SyncEngine]. Sync is strictly opt-in and user-triggered. */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDatabase,
    client: OkHttpClient,
    gson: Gson,
    private val link: LinkManager,
) {
    data class UiState(
        val paired: Boolean = false,
        val hubUrl: String = "",
        val busy: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false,
        val lastSyncAtMs: Long? = null,
        val autoSync: Boolean = false,
    )

    private val prefs = context.getSharedPreferences(SharedPrefsSyncStateStore.FILE, Context.MODE_PRIVATE)
    private val stateStore = SharedPrefsSyncStateStore(context)
    private val engine = SyncEngine(RoomSyncStore(db), LinkAwareSyncApi(HttpSyncApi(client, gson), link), stateStore)
    private val lock = Mutex()

    private val _ui = MutableStateFlow(snapshot())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private fun snapshot(message: String? = null, isError: Boolean = false, busy: Boolean = false): UiState {
        val s = stateStore.load()
        return UiState(s.isPaired, if (s.hubUrl.startsWith(LINK_SCHEME)) "your desktop (from anywhere)" else s.hubUrl, busy, message, isError, s.lastSyncAtMs, s.isPaired && autoSyncEnabled())
    }

    // Kept beside the sync state but outside SyncState: it is a UI preference, not protocol state.
    private fun autoSyncEnabled() = prefs.getBoolean(K_AUTO, false)

    /** Turns the optional hourly background sync on or off. Off by default; needs a paired hub. */
    fun setAutoSync(enabled: Boolean) {
        val on = enabled && stateStore.load().isPaired
        prefs.edit().putBoolean(K_AUTO, on).apply()
        if (on) SyncScheduler.enable(context) else SyncScheduler.disable(context)
        _ui.value = snapshot(if (on) "Automatic sync is on (about hourly, on any network)." else "Automatic sync is off.")
    }

    /** Used by the background worker; quiet on failure, and skipped if a sync is already running. */
    suspend fun syncInBackground() {
        if (!stateStore.load().isPaired || !autoSyncEnabled()) return
        run(null) { describe(engine.sync()) }
    }

    suspend fun pair(hubUrl: String, key: String) = run("Paired with the hub. Syncing…") {
        engine.pair(hubUrl, key, "Android ${Build.MODEL}")
        _ui.value = snapshot("Paired. Syncing…", busy = true)
        describe(engine.sync())
    }

    /** Pairs over the Transferd link: the desktop's invite finds the desktop, the pairing key authorises sync. */
    suspend fun pairViaLink(code: String, typedKey: String) = run("Connecting…") {
        val p = link.decode(code)
        val key = p.key ?: typedKey
        if (key.isBlank()) throw SyncException("Enter the pairing key shown on the desktop", SyncException.Kind.PROTOCOL)
        val nodeId = link.acceptInvite(p.invite)
        engine.pair(LINK_SCHEME + nodeId, key, "Android ${Build.MODEL}")
        _ui.value = snapshot("Paired. Syncing…", busy = true)
        describe(engine.sync())
    }

    val linkAvailable: Boolean get() = link.isAvailable

    suspend fun syncNow() = run(null) { describe(engine.sync()) }

    fun unpair() {
        prefs.edit().putBoolean(K_AUTO, false).apply()
        SyncScheduler.disable(context)
        engine.unpair()
        _ui.value = snapshot("Unpaired. Your data on this device was not changed.")
    }

    private fun describe(r: SyncResult): String {
        val base = "Synced: sent ${r.pushed}, received ${r.pulled}"
        return if (r.rejected.isEmpty()) base
        else "$base. ${r.rejected.size} item(s) were rejected by the hub and will be retried."
    }

    private companion object {
        const val K_AUTO = "auto_sync"
    }

    private suspend fun run(startMessage: String?, block: suspend () -> String) {
        if (!lock.tryLock()) return
        try {
            _ui.value = snapshot(startMessage, busy = true)
            val message = try {
                block()
            } catch (e: SyncException) {
                _ui.value = snapshot(e.message, isError = true)
                return
            } catch (e: Exception) {
                _ui.value = snapshot("Sync failed: ${e.message}", isError = true)
                return
            }
            _ui.value = snapshot(message)
        } finally {
            lock.unlock()
        }
    }
}
