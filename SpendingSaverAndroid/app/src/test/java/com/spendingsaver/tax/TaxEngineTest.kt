package com.spendingsaver.tax

import com.spendingsaver.data.Category
import com.spendingsaver.data.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TaxEngineTest {
    private fun p(year: Int = 2026, f: FilingStatus = FilingStatus.SINGLE, wages: Double = 0.0, se: Double = 0.0, block: TaxProfile.() -> TaxProfile = { this }) =
        TaxProfile(year = year, filing = f, wages = wages, selfEmployment = se).block()

    @Test fun singleFilerOn2026Wages() {
        // 60,000 - 16,100 standard = 43,900. 10% of 12,400 + 12% of 31,500 = 1,240 + 3,780 = 5,020.
        val r = TaxEngine.estimate(p(wages = 60_000.0))
        assertEquals(43_900.0, r.taxableIncome, 0.01)
        assertEquals(5_020.0, r.incomeTax, 0.01)
        assertEquals(0.12, r.marginalRate, 0.0)
        assertEquals(60_000 * 0.0765, r.employeeFica, 0.01)
    }

    @Test fun incomeBelowDeductionOwesNothing() {
        val r = TaxEngine.estimate(p(wages = 10_000.0))
        assertEquals(0.0, r.taxableIncome, 0.0)
        assertEquals(0.0, r.incomeTax, 0.0)
        assertEquals(0.0, r.marginalRate, 0.0)
    }

    @Test fun marriedJointUses2025Table() {
        // 100,000 - 31,500 = 68,500. 10% of 23,850 + 12% of 44,650 = 2,385 + 5,358 = 7,743.
        val r = TaxEngine.estimate(p(year = 2025, f = FilingStatus.MARRIED_JOINT, wages = 100_000.0))
        assertEquals(7_743.0, r.incomeTax, 0.01)
    }

    @Test fun selfEmploymentTaxAndHalfDeduction() {
        val r = TaxEngine.estimate(p(se = 50_000.0))
        val expectedSe = 50_000 * 0.9235 * 0.153
        assertEquals(expectedSe, r.selfEmploymentTax, 0.01)
        assertEquals(50_000 - expectedSe / 2, r.adjustedGross, 0.01)
    }

    @Test fun socialSecurityStopsAtWageBase() {
        val r = TaxEngine.estimate(p(wages = 300_000.0))
        assertEquals(0.062 * 184_500 + 0.0145 * 300_000, r.employeeFica, 0.01)
        assertTrue(r.additionalMedicare > 0.0)
    }

    @Test fun childCreditReducesTaxButNotBelowZero() {
        val r = TaxEngine.estimate(p(wages = 60_000.0) { copy(children = 2) })
        assertEquals(4_400.0 .coerceAtMost(5_020.0), r.childCredit, 0.01)
        assertEquals(5_020.0 - 4_400.0, r.incomeTax, 0.01)
        val big = TaxEngine.estimate(p(wages = 25_000.0) { copy(children = 3) })
        assertEquals(0.0, big.incomeTax, 0.0)
    }

    @Test fun itemizedOverridesStandard() {
        val r = TaxEngine.estimate(p(wages = 100_000.0) { copy(useItemized = true, itemized = 30_000.0) })
        assertEquals(70_000.0, r.taxableIncome, 0.01)
    }

    @Test fun stateTaxUsesUserRateButNoTaxStatesAreZero() {
        val ca = TaxEngine.estimate(p(wages = 60_000.0) { copy(state = "CA", stateRatePercent = 5.0) })
        assertEquals(43_900 * 0.05, ca.stateTax, 0.01)
        val tx = TaxEngine.estimate(p(wages = 60_000.0) { copy(state = "TX", stateRatePercent = 5.0) })
        assertEquals(0.0, tx.stateTax, 0.0)
    }

    @Test fun balanceAndRefundFromPayments() {
        val owed = TaxEngine.estimate(p(wages = 60_000.0) { copy(federalWithheld = 3_000.0) })
        assertEquals(2_020.0, owed.balance, 0.01)
        val refund = TaxEngine.estimate(p(wages = 60_000.0) { copy(federalWithheld = 6_000.0) })
        assertTrue(refund.balance < 0)
        assertEquals(0.0, TaxEngine.estimate(p(wages = 60_000.0) { copy(federalWithheld = 9_000.0) }).quarterly, 0.0)
    }

    @Test fun yearSummaryCountsOnlyThatYearAndDeductibleCategories() {
        val cats = listOf(Category(id = "o", name = "Office"), Category(id = "f", name = "Food"))
        val tx = listOf(
            Transaction(accountId = "a", amount = -100.0, description = "Paper", categoryId = "o", date = "2026-03-01"),
            Transaction(accountId = "a", amount = -50.0, description = "Lunch", categoryId = "f", date = "2026-03-02"),
            Transaction(accountId = "a", amount = -999.0, description = "Old", categoryId = "o", date = "2025-03-01"),
            Transaction(accountId = "a", amount = 5_000.0, description = "Pay", date = "2026-04-01"),
        )
        val s = TaxSummary.forYear(2026, tx, cats, setOf("o"))
        assertEquals(5_000.0, s.income, 0.0)
        assertEquals(150.0, s.expenses, 0.0)
        assertEquals(100.0, s.deductible, 0.0)
        assertEquals("Office", s.deductibleByCategory.single().first)
    }
}
