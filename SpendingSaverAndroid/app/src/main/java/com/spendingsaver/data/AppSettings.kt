package com.spendingsaver.data

/**
 * Serializable app settings/preferences. Persisted via SharedPreferences.
 */
data class AppSettings(
    val themeMode: String = "system",      // "system" | "light" | "dark"
    val accentColor: String = "teal",      // "teal" | "blue" | "green" | "purple" | "orange" | "red"
    val textScale: Float = 1.0f,          // 0.85f .. 1.3f
    val showBalance: Boolean = true,       // hide sensitive balances
    val compactMode: Boolean = false,       // denser list rows
    val defaultCurrency: String = "USD",
    val dateFormat: String = "yyyy-MM-dd",  // "yyyy-MM-dd" | "MM/dd/yyyy" | "dd/MM/yyyy"
)