package com.spendingsaver.repository

import android.content.Context
import android.content.SharedPreferences
import com.spendingsaver.data.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent settings repository backed by SharedPreferences.
 * Exposes a StateFlow so the UI (theme, nav) reacts instantly to changes.
 */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("spendingsaver_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        themeMode = prefs.getString(KEY_THEME, "system") ?: "system",
        accentColor = prefs.getString(KEY_ACCENT, "teal") ?: "teal",
        textScale = prefs.getFloat(KEY_TEXT_SCALE, 1.0f),
        showBalance = prefs.getBoolean(KEY_SHOW_BALANCE, true),
        compactMode = prefs.getBoolean(KEY_COMPACT, false),
        defaultCurrency = prefs.getString(KEY_CURRENCY, "USD") ?: "USD",
        dateFormat = prefs.getString(KEY_DATE_FORMAT, "yyyy-MM-dd") ?: "yyyy-MM-dd",
        syncEnabled = prefs.getBoolean(KEY_SYNC, true),
        syncIntervalMinutes = prefs.getInt(KEY_SYNC_INTERVAL, 15),
        syncHost = prefs.getString(KEY_SYNC_HOST, "") ?: "",
        pairingKey = prefs.getString(KEY_PAIRING_KEY, "") ?: "",
    )

    private fun update(mutator: (AppSettings) -> AppSettings) {
        val updated = mutator(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putString(KEY_THEME, updated.themeMode)
            .putString(KEY_ACCENT, updated.accentColor)
            .putFloat(KEY_TEXT_SCALE, updated.textScale)
            .putBoolean(KEY_SHOW_BALANCE, updated.showBalance)
            .putBoolean(KEY_COMPACT, updated.compactMode)
            .putString(KEY_CURRENCY, updated.defaultCurrency)
            .putString(KEY_DATE_FORMAT, updated.dateFormat)
            .putBoolean(KEY_SYNC, updated.syncEnabled)
            .putInt(KEY_SYNC_INTERVAL, updated.syncIntervalMinutes)
            .putString(KEY_SYNC_HOST, updated.syncHost)
            .putString(KEY_PAIRING_KEY, updated.pairingKey)
            .apply()
    }

    fun setTheme(mode: String) = update { it.copy(themeMode = mode) }
    fun setAccent(color: String) = update { it.copy(accentColor = color) }
    fun setTextScale(scale: Float) = update { it.copy(textScale = scale) }
    fun setShowBalance(show: Boolean) = update { it.copy(showBalance = show) }
    fun setCompact(compact: Boolean) = update { it.copy(compactMode = compact) }
    fun setCurrency(c: String) = update { it.copy(defaultCurrency = c) }
    fun setDateFormat(f: String) = update { it.copy(dateFormat = f) }
    fun setSyncEnabled(enabled: Boolean) = update { it.copy(syncEnabled = enabled) }
    fun setSyncInterval(minutes: Int) = update { it.copy(syncIntervalMinutes = minutes) }
    fun setSyncHost(host: String) = update { it.copy(syncHost = host) }
    fun setPairingKey(key: String) = update { it.copy(pairingKey = key) }

    fun reset() {
        update { AppSettings() }
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_SHOW_BALANCE = "show_balance"
        private const val KEY_COMPACT = "compact_mode"
        private const val KEY_CURRENCY = "default_currency"
        private const val KEY_DATE_FORMAT = "date_format"
        private const val KEY_SYNC = "sync_enabled"
        private const val KEY_SYNC_INTERVAL = "sync_interval"
        private const val KEY_SYNC_HOST = "sync_host"
        private const val KEY_PAIRING_KEY = "pairing_key"
    }
}