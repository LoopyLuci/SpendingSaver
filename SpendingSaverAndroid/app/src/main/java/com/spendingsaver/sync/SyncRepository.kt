package com.spendingsaver.sync

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.spendingsaver.data.AppDatabase
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
    @ApplicationContext context: Context,
    db: AppDatabase,
    client: OkHttpClient,
    gson: Gson,
) {
    data class UiState(
        val paired: Boolean = false,
        val hubUrl: String = "",
        val busy: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false,
        val lastSyncAtMs: Long? = null,
    )

    private val stateStore = SharedPrefsSyncStateStore(context)
    private val engine = SyncEngine(RoomSyncStore(db), HttpSyncApi(client, gson), stateStore)
    private val lock = Mutex()

    private val _ui = MutableStateFlow(snapshot())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private fun snapshot(message: String? = null, isError: Boolean = false, busy: Boolean = false): UiState {
        val s = stateStore.load()
        return UiState(s.isPaired, s.hubUrl, busy, message, isError, s.lastSyncAtMs)
    }

    suspend fun pair(hubUrl: String, key: String) = run("Paired with the hub. Syncing…") {
        engine.pair(hubUrl, key, "Android ${Build.MODEL}")
        _ui.value = snapshot("Paired. Syncing…", busy = true)
        describe(engine.sync())
    }

    suspend fun syncNow() = run(null) { describe(engine.sync()) }

    fun unpair() {
        engine.unpair()
        _ui.value = snapshot("Unpaired. Your data on this device was not changed.")
    }

    private fun describe(r: SyncResult): String {
        val base = "Synced: sent ${r.pushed}, received ${r.pulled}"
        return if (r.rejected.isEmpty()) base
        else "$base. ${r.rejected.size} item(s) were rejected by the hub and will be retried."
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
