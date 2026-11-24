package com.spendingsaver.tax

import kotlin.math.max
import kotlin.math.min

/**
 * On-device US federal tax estimator. Pure Kotlin: no network, no storage, no data pack.
 *
 * It is an ESTIMATE for planning, not tax advice and not a filing. It does not model: the qualified
 * business income deduction, itemized-deduction limits, AMT, capital-gain rates, the earned income or
 * other credits, or state rules beyond one effective rate the user enters.
 * Figures come from IRS Rev. Proc. 2025-32 (2026) and Rev. Proc. 2024-40 as amended by the 2025 tax law (2025);
 * the Social Security wage base is from the SSA announcement. Update [TaxTables] each year.
 */
enum class FilingStatus(val label: String) {
    SINGLE("Single"),
    MARRIED_JOINT("Married filing jointly"),
    MARRIED_SEPARATE("Married filing separately"),
    HEAD_OF_HOUSEHOLD("Head of household");

    companion object {
        fun fromLabel(label: String) = values().firstOrNull { it.label == label } ?: SINGLE
    }
}

/** One year's constants. [bracketTops] are the upper edges of the first six brackets (10%..35%); 37% is the rest. */
data class YearTable(
    val standardDeduction: Map<FilingStatus, Double>,
    val bracketTops: Map<FilingStatus, List<Double>>,
    val socialSecurityWageBase: Double,
    val childTaxCredit: Double,
)

object TaxTables {
    val RATES = listOf(0.10, 0.12, 0.22, 0.24, 0.32, 0.35, 0.37)

    private val Y2025 = YearTable(
        standardDeduction = mapOf(
            FilingStatus.SINGLE to 15_750.0, FilingStatus.MARRIED_SEPARATE to 15_750.0,
            FilingStatus.MARRIED_JOINT to 31_500.0, FilingStatus.HEAD_OF_HOUSEHOLD to 23_625.0,
        ),
        bracketTops = mapOf(
            FilingStatus.SINGLE to listOf(11_925.0, 48_475.0, 103_350.0, 197_300.0, 250_525.0, 626_350.0),
            FilingStatus.MARRIED_SEPARATE to listOf(11_925.0, 48_475.0, 103_350.0, 197_300.0, 250_525.0, 375_800.0),
            FilingStatus.MARRIED_JOINT to listOf(23_850.0, 96_950.0, 206_700.0, 394_600.0, 501_050.0, 751_600.0),
            FilingStatus.HEAD_OF_HOUSEHOLD to listOf(17_000.0, 64_850.0, 103_350.0, 197_300.0, 250_500.0, 626_350.0),
        ),
        socialSecurityWageBase = 176_100.0,
        childTaxCredit = 2_200.0,
    )

    private val Y2026 = YearTable(
        standardDeduction = mapOf(
            FilingStatus.SINGLE to 16_100.0, FilingStatus.MARRIED_SEPARATE to 16_100.0,
            FilingStatus.MARRIED_JOINT to 32_200.0, FilingStatus.HEAD_OF_HOUSEHOLD to 24_150.0,
        ),
        bracketTops = mapOf(
            FilingStatus.SINGLE to listOf(12_400.0, 50_400.0, 105_700.0, 201_775.0, 256_225.0, 640_600.0),
            FilingStatus.MARRIED_SEPARATE to listOf(12_400.0, 50_400.0, 105_700.0, 201_775.0, 256_225.0, 384_350.0),
            FilingStatus.MARRIED_JOINT to listOf(24_800.0, 100_800.0, 211_400.0, 403_550.0, 512_450.0, 768_700.0),
            FilingStatus.HEAD_OF_HOUSEHOLD to listOf(17_700.0, 67_450.0, 105_700.0, 201_750.0, 256_200.0, 640_600.0),
        ),
        socialSecurityWageBase = 184_500.0,
        childTaxCredit = 2_200.0,
    )

    val YEARS: Map<Int, YearTable> = mapOf(2025 to Y2025, 2026 to Y2026)
    val SUPPORTED_YEARS: List<Int> = YEARS.keys.sortedDescending()
    fun forYear(year: Int): YearTable = YEARS[year] ?: YEARS.getValue(YEARS.keys.max())
}

/** US states with no tax on wages. Every other state needs the user's own effective rate. */
object States {
    val NO_INCOME_TAX = setOf("AK", "FL", "NV", "NH", "SD", "TN", "TX", "WA", "WY")
    val ALL = listOf(
        "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas", "CA" to "California",
        "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware", "DC" to "District of Columbia",
        "FL" to "Florida", "GA" to "Georgia", "HI" to "Hawaii", "ID" to "Idaho", "IL" to "Illinois",
        "IN" to "Indiana", "IA" to "Iowa", "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana",
        "ME" to "Maine", "MD" to "Maryland", "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota",
        "MS" to "Mississippi", "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada",
        "NH" to "New Hampshire", "NJ" to "New Jersey", "NM" to "New Mexico", "NY" to "New York",
        "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio", "OK" to "Oklahoma", "OR" to "Oregon",
        "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina", "SD" to "South Dakota",
        "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah", "VT" to "Vermont", "VA" to "Virginia",
        "WA" to "Washington", "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming",
    )
    fun label(code: String) = ALL.firstOrNull { it.first == code }?.second ?: code
}

data class TaxProfile(
    val year: Int = 2026,
    val filing: FilingStatus = FilingStatus.SINGLE,
    /** W-2 wages. */
    val wages: Double = 0.0,
    /** Net profit from self-employment (after business expenses). */
    val selfEmployment: Double = 0.0,
    /** Interest, dividends and other taxable income. */
    val otherIncome: Double = 0.0,
    /** Above-the-line adjustments such as IRA or HSA contributions. */
    val adjustments: Double = 0.0,
    val useItemized: Boolean = false,
    val itemized: Double = 0.0,
    /** Qualifying children under 17. */
    val children: Int = 0,
    val federalWithheld: Double = 0.0,
    val estimatedPaid: Double = 0.0,
    val state: String = "",
    /** Effective state income tax rate, in percent of taxable income. */
    val stateRatePercent: Double = 0.0,
)

data class Bracket(val rate: Double, val from: Double, val to: Double?, val taxed: Double, val tax: Double)

data class TaxResult(
    val grossIncome: Double,
    val adjustedGross: Double,
    val deduction: Double,
    val taxableIncome: Double,
    val brackets: List<Bracket>,
    val incomeTaxBeforeCredits: Double,
    val childCredit: Double,
    val incomeTax: Double,
    val selfEmploymentTax: Double,
    val additionalMedicare: Double,
    /** Employee Social Security and Medicare on wages; assumed already withheld by the employer. */
    val employeeFica: Double,
    val stateTax: Double,
    val marginalRate: Double,
    /** Income tax + self-employment tax + state tax: what you pay with the return or in estimates. */
    val dueBeforePayments: Double,
    val totalPaid: Double,
    /** Positive: still owed. Negative: refund. */
    val balance: Double,
    /** All taxes including payroll tax, as a share of gross income. */
    val effectiveRate: Double,
    val quarterly: Double,
)

object TaxEngine {
    private const val SE_FACTOR = 0.9235

    fun bracketTax(taxable: Double, filing: FilingStatus, year: Int): Pair<List<Bracket>, Double> {
        val tops = TaxTables.forYear(year).bracketTops.getValue(filing)
        val rows = mutableListOf<Bracket>()
        var lower = 0.0
        var total = 0.0
        TaxTables.RATES.forEachIndexed { i, rate ->
            val top = tops.getOrNull(i)
            val upper = top ?: Double.MAX_VALUE
            val slice = max(0.0, min(taxable, upper) - lower)
            val tax = slice * rate
            rows += Bracket(rate, lower, top, slice, tax)
            total += tax
            lower = upper
        }
        return rows to total
    }

    fun estimate(p: TaxProfile): TaxResult {
        val t = TaxTables.forYear(p.year)
        val wages = max(0.0, p.wages)
        val se = max(0.0, p.selfEmployment)
        val other = max(0.0, p.otherIncome)

        // Self-employment tax: 15.3% of 92.35% of net profit; the Social Security part stops at the wage base shared with wages.
        val seBase = se * SE_FACTOR
        val seSs = 0.124 * min(seBase, max(0.0, t.socialSecurityWageBase - wages))
        val seMedicare = 0.029 * seBase
        val seTax = if (seBase < 400.0) 0.0 else seSs + seMedicare // owed only from $400 of net earnings

        val gross = wages + se + other
        val agi = max(0.0, gross - 0.5 * seTax - max(0.0, p.adjustments))
        val standard = t.standardDeduction.getValue(p.filing)
        val deduction = if (p.useItemized) max(0.0, p.itemized) else standard
        val taxable = max(0.0, agi - deduction)

        val (brackets, taxBefore) = bracketTax(taxable, p.filing, p.year)

        val phaseStart = if (p.filing == FilingStatus.MARRIED_JOINT) 400_000.0 else 200_000.0
        val over = max(0.0, agi - phaseStart)
        val reduction = Math.ceil(over / 1000.0) * 50.0
        val credit = min(taxBefore, max(0.0, p.children * t.childTaxCredit - reduction))
        val incomeTax = taxBefore - credit

        val medicareThreshold = when (p.filing) {
            FilingStatus.MARRIED_JOINT -> 250_000.0
            FilingStatus.MARRIED_SEPARATE -> 125_000.0
            else -> 200_000.0
        }
        val addMedicare = 0.009 * max(0.0, wages + seBase - medicareThreshold)
        val fica = 0.062 * min(wages, t.socialSecurityWageBase) + 0.0145 * wages

        val stateRate = if (p.state in States.NO_INCOME_TAX) 0.0 else max(0.0, p.stateRatePercent) / 100.0
        val stateTax = taxable * stateRate

        val due = incomeTax + seTax + addMedicare + stateTax
        val paid = max(0.0, p.federalWithheld) + max(0.0, p.estimatedPaid)
        val marginal = if (taxable <= 0.0) 0.0 else brackets.last { it.taxed > 0.0 }.rate
        val effective = if (gross > 0.0) (due + fica) / gross else 0.0
        val quarterly = max(0.0, due - max(0.0, p.federalWithheld)) / 4.0

        return TaxResult(
            grossIncome = gross, adjustedGross = agi, deduction = deduction, taxableIncome = taxable,
            brackets = brackets, incomeTaxBeforeCredits = taxBefore, childCredit = credit, incomeTax = incomeTax,
            selfEmploymentTax = seTax, additionalMedicare = addMedicare, employeeFica = fica, stateTax = stateTax,
            marginalRate = marginal, dueBeforePayments = due, totalPaid = paid, balance = due - paid,
            effectiveRate = effective, quarterly = quarterly,
        )
    }

    /** Estimated-payment due dates for a tax year (US individuals, weekends not adjusted). */
    fun quarterlyDates(year: Int) = listOf("Apr 15, $year", "Jun 15, $year", "Sep 15, $year", "Jan 15, ${year + 1}")
}
