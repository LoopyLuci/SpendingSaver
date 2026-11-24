package com.spendingsaver.util

import com.spendingsaver.data.Transaction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Calendar-window logic for budgets: weekly (Mon-Sun), monthly, yearly. */
object BudgetPeriod {
    fun windowStart(period: String, today: LocalDate): LocalDate = when (period.lowercase()) {
        "weekly" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        "yearly" -> today.withDayOfYear(1)
        else -> today.withDayOfMonth(1)
    }

    fun windowEnd(period: String, today: LocalDate): LocalDate = when (period.lowercase()) {
        "weekly" -> windowStart(period, today).plusDays(6)
        "yearly" -> today.withDayOfYear(today.lengthOfYear())
        else -> today.withDayOfMonth(today.lengthOfMonth())
    }

    /** Expenses (negative amounts) in [categoryId] dated inside the current window, as a positive total. */
    fun spent(
        transactions: List<Transaction>,
        categoryId: String,
        period: String,
        today: LocalDate = LocalDate.now(),
    ): Double {
        val start = windowStart(period, today)
        val end = windowEnd(period, today)
        return transactions.filter { tx ->
            tx.categoryId == categoryId && tx.amount < 0 &&
                runCatching { LocalDate.parse(tx.date.take(10)) }.getOrNull()?.let { it in start..end } == true
        }.sumOf { Math.abs(it.amount) }
    }
}
