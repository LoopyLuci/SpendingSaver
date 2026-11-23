package com.spendingsaver.sync

import android.content.Context

/**
 * Stores the pairing token and sync cursors in an app-private preferences file.
 *
 * The file `spendingsaver_sync.xml` is excluded from Android cloud backup and
 * device transfer (see `res/xml/backup_rules.xml` and `data_extraction_rules.xml`)
 * so the device token never leaves this device that way. It is not additionally
 * encrypted; anyone with root access to the device could read it.
 */
class SharedPrefsSyncStateStore(context: Context) : SyncStateStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load() = SyncState(
        hubUrl = prefs.getString(K_URL, "") ?: "",
        deviceId = prefs.getString(K_DEVICE, "") ?: "",
        token = prefs.getString(K_TOKEN, null),
        cursor = prefs.getLong(K_CURSOR, 0),
        lastPushMs = prefs.getLong(K_PUSH, 0),
        lastSyncAtMs = if (prefs.contains(K_LAST)) prefs.getLong(K_LAST, 0) else null,
    )

    override fun save(state: SyncState) {
        prefs.edit()
            .putString(K_URL, state.hubUrl)
            .putString(K_DEVICE, state.deviceId)
            .apply { if (state.token == null) remove(K_TOKEN) else putString(K_TOKEN, state.token) }
            .putLong(K_CURSOR, state.cursor)
            .putLong(K_PUSH, state.lastPushMs)
            .apply { if (state.lastSyncAtMs == null) remove(K_LAST) else putLong(K_LAST, state.lastSyncAtMs) }
            .apply()
    }

    companion object {
        const val FILE = "spendingsaver_sync"
        private const val K_URL = "hub_url"
        private const val K_DEVICE = "device_id"
        private const val K_TOKEN = "token"
        private const val K_CURSOR = "cursor"
        private const val K_PUSH = "last_push_ms"
        private const val K_LAST = "last_sync_at_ms"
    }
}
