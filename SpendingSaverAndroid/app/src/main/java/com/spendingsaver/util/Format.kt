package com.spendingsaver.util

import com.spendingsaver.data.AppSettings
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Locale

/**
 * Formatting helpers that respect the user's settings (currency, date format).
 */
object Format {
    private val SYMBOLS = mapOf(
        "USD" to "$", "EUR" to "€", "GBP" to "£", "CAD" to "C$",
        "AUD" to "A$", "JPY" to "¥", "CHF" to "CHF ", "SEK" to "kr ",
        "NOK" to "kr ", "DKK" to "kr ", "PLN" to "zł ", "INR" to "₹",
        "BRL" to "R$", "MXN" to "MX$", "CNY" to "¥",
    )

    fun currencySymbol(code: String?): String {
        val c = code ?: return "$"
        return SYMBOLS[c.uppercase()] ?: "?$c "
    }

    fun money(amount: Double, currency: String?): String {
        val symbol = currencySymbol(currency)
        val nf = NumberFormat.getNumberInstance(Locale.US)
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        val formatted = nf.format(Math.abs(amount))
        val sign = if (amount < 0) "-" else ""
        return "$sign$symbol$formatted"
    }

    fun moneySigned(amount: Double, currency: String?): String {
        return money(amount, currency)
    }

    /**
     * Format a YYYY-MM-DD date per the user's setting.
     */
    fun date(iso: String?, formatSetting: String): String {
        if (iso == null || iso.isBlank()) return ""
        // Accept iso 'yyyy-MM-dd' or already-formatted.
        val source = kotlin.runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso) }.getOrNull()
        if (source == null) return iso
        val out = SimpleDateFormat(formatSetting, Locale.US)
        return out.format(source)
    }

    /** Display a date range / today label. */
    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
}