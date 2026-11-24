package com.spendingsaver.util

import com.spendingsaver.data.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BudgetPeriodTest {
    private val today = LocalDate.of(2026, 11, 25) // Wednesday

    private fun tx(date: String, amount: Double, cat: String? = "c1") =
        Transaction(accountId = "a", amount = amount, description = "x", categoryId = cat, date = date)

    @Test
    fun `window starts`() {
        assertEquals(LocalDate.of(2026, 11, 23), BudgetPeriod.windowStart("weekly", today))
        assertEquals(LocalDate.of(2026, 11, 1), BudgetPeriod.windowStart("monthly", today))
        assertEquals(LocalDate.of(2026, 1, 1), BudgetPeriod.windowStart("yearly", today))
    }

    @Test
    fun `monthly counts only current month expenses in category`() {
        val txs = listOf(
            tx("2026-11-02", -10.0),
            tx("2026-11-30", -5.0),
            tx("2026-10-31", -100.0), // last month
            tx("2026-12-01", -100.0), // next month
            tx("2026-11-10", 50.0),   // income
            tx("2026-11-10", -7.0, cat = "other"),
            tx("garbage", -9.0),
        )
        assertEquals(15.0, BudgetPeriod.spent(txs, "c1", "monthly", today), 0.001)
    }

    @Test
    fun `weekly and yearly windows`() {
        val txs = listOf(tx("2026-11-22", -1.0), tx("2026-11-23", -2.0), tx("2026-03-01", -4.0))
        assertEquals(2.0, BudgetPeriod.spent(txs, "c1", "weekly", today), 0.001)
        assertEquals(7.0, BudgetPeriod.spent(txs, "c1", "yearly", today), 0.001)
    }
}
