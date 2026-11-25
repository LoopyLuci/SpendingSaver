/**
 * US federal tax estimate. A direct port of the Android app's TaxEngine.kt (same constants, same
 * formulas, same simplifications) so both apps give the same numbers; see tax.test.ts, which repeats the
 * Android TaxEngineTest cases. It is an ESTIMATE for planning, not tax advice. It does not model the
 * qualified business income deduction, itemized-deduction limits, AMT, capital-gain rates, the earned
 * income or other credits, or state rules beyond one effective rate you enter.
 * Figures: IRS Rev. Proc. 2025-32 (2026), Rev. Proc. 2024-40 as amended by the 2025 tax law (2025); SSA wage base.
 * Update TABLES each year (and keep it in step with the Android tax/TaxEngine.kt).
 * Amounts are dollars (floating point), like the Android engine; they are never stored as ledger money.
 */
export type FilingStatus = 'SINGLE' | 'MARRIED_JOINT' | 'MARRIED_SEPARATE' | 'HEAD_OF_HOUSEHOLD'

export const FILING_LABELS: Record<FilingStatus, string> = {
  SINGLE: 'Single',
  MARRIED_JOINT: 'Married filing jointly',
  MARRIED_SEPARATE: 'Married filing separately',
  HEAD_OF_HOUSEHOLD: 'Head of household',
}

interface YearTable {
  standardDeduction: Record<FilingStatus, number>
  /** Upper edges of the first six brackets (10%..35%); 37% is the rest. */
  bracketTops: Record<FilingStatus, number[]>
  socialSecurityWageBase: number
  childTaxCredit: number
}

export const RATES = [0.10, 0.12, 0.22, 0.24, 0.32, 0.35, 0.37]

const Y2025: YearTable = {
  standardDeduction: { SINGLE: 15750, MARRIED_SEPARATE: 15750, MARRIED_JOINT: 31500, HEAD_OF_HOUSEHOLD: 23625 },
  bracketTops: {
    SINGLE: [11925, 48475, 103350, 197300, 250525, 626350],
    MARRIED_SEPARATE: [11925, 48475, 103350, 197300, 250525, 375800],
    MARRIED_JOINT: [23850, 96950, 206700, 394600, 501050, 751600],
    HEAD_OF_HOUSEHOLD: [17000, 64850, 103350, 197300, 250500, 626350],
  },
  socialSecurityWageBase: 176100,
  childTaxCredit: 2200,
}

const Y2026: YearTable = {
  standardDeduction: { SINGLE: 16100, MARRIED_SEPARATE: 16100, MARRIED_JOINT: 32200, HEAD_OF_HOUSEHOLD: 24150 },
  bracketTops: {
    SINGLE: [12400, 50400, 105700, 201775, 256225, 640600],
    MARRIED_SEPARATE: [12400, 50400, 105700, 201775, 256225, 384350],
    MARRIED_JOINT: [24800, 100800, 211400, 403550, 512450, 768700],
    HEAD_OF_HOUSEHOLD: [17700, 67450, 105700, 201750, 256200, 640600],
  },
  socialSecurityWageBase: 184500,
  childTaxCredit: 2200,
}

export const TAX_YEARS: Record<number, YearTable> = { 2025: Y2025, 2026: Y2026 }
export const SUPPORTED_YEARS = Object.keys(TAX_YEARS).map(Number).sort((a, b) => b - a)

export function tableForYear(year: number): YearTable {
  return TAX_YEARS[year] ?? TAX_YEARS[Math.max(...Object.keys(TAX_YEARS).map(Number))]
}

/** US states with no tax on wages. Every other state needs the user's own effective rate. */
export const NO_INCOME_TAX_STATES = new Set(['AK', 'FL', 'NV', 'NH', 'SD', 'TN', 'TX', 'WA', 'WY'])

export interface TaxProfile {
  year: number
  filing: FilingStatus
  wages: number
  selfEmployment: number
  otherIncome: number
  adjustments: number
  useItemized: boolean
  itemized: number
  children: number
  federalWithheld: number
  estimatedPaid: number
  state: string
  stateRatePercent: number
}

export const defaultTaxProfile = (): TaxProfile => ({
  year: 2026, filing: 'SINGLE', wages: 0, selfEmployment: 0, otherIncome: 0, adjustments: 0, useItemized: false,
  itemized: 0, children: 0, federalWithheld: 0, estimatedPaid: 0, state: '', stateRatePercent: 0,
})

export interface Bracket { rate: number; from: number; to: number | null; taxed: number; tax: number }

export interface TaxResult {
  grossIncome: number
  adjustedGross: number
  deduction: number
  taxableIncome: number
  brackets: Bracket[]
  incomeTaxBeforeCredits: number
  childCredit: number
  incomeTax: number
  selfEmploymentTax: number
  additionalMedicare: number
  employeeFica: number
  stateTax: number
  marginalRate: number
  dueBeforePayments: number
  totalPaid: number
  /** Positive: still owed. Negative: refund. */
  balance: number
  effectiveRate: number
  quarterly: number
}

const SE_FACTOR = 0.9235
const max = Math.max
const min = Math.min

export function bracketTax(taxable: number, filing: FilingStatus, year: number): { brackets: Bracket[]; total: number } {
  const tops = tableForYear(year).bracketTops[filing]
  const rows: Bracket[] = []
  let lower = 0, total = 0
  RATES.forEach((rate, i) => {
    const top = tops[i] ?? null
    const upper = top ?? Number.MAX_VALUE
    const slice = max(0, min(taxable, upper) - lower)
    const tax = slice * rate
    rows.push({ rate, from: lower, to: top, taxed: slice, tax })
    total += tax
    lower = upper
  })
  return { brackets: rows, total }
}

export function estimateTax(p: TaxProfile): TaxResult {
  const t = tableForYear(p.year)
  const wages = max(0, p.wages)
  const se = max(0, p.selfEmployment)
  const other = max(0, p.otherIncome)

  // Self-employment tax: 15.3% of 92.35% of net profit; the Social Security part stops at the wage base shared with wages.
  const seBase = se * SE_FACTOR
  const seSs = 0.124 * min(seBase, max(0, t.socialSecurityWageBase - wages))
  const seMedicare = 0.029 * seBase
  const seTax = seBase < 400 ? 0 : seSs + seMedicare // owed only from $400 of net earnings

  const gross = wages + se + other
  const agi = max(0, gross - 0.5 * seTax - max(0, p.adjustments))
  const standard = t.standardDeduction[p.filing]
  const deduction = p.useItemized ? max(0, p.itemized) : standard
  const taxable = max(0, agi - deduction)

  const { brackets, total: taxBefore } = bracketTax(taxable, p.filing, p.year)

  const phaseStart = p.filing === 'MARRIED_JOINT' ? 400000 : 200000
  const over = max(0, agi - phaseStart)
  const reduction = Math.ceil(over / 1000) * 50
  const credit = min(taxBefore, max(0, p.children * t.childTaxCredit - reduction))
  const incomeTax = taxBefore - credit

  const medicareThreshold = p.filing === 'MARRIED_JOINT' ? 250000 : p.filing === 'MARRIED_SEPARATE' ? 125000 : 200000
  const addMedicare = 0.009 * max(0, wages + seBase - medicareThreshold)
  const fica = 0.062 * min(wages, t.socialSecurityWageBase) + 0.0145 * wages

  const stateRate = NO_INCOME_TAX_STATES.has(p.state) ? 0 : max(0, p.stateRatePercent) / 100
  const stateTax = taxable * stateRate

  const due = incomeTax + seTax + addMedicare + stateTax
  const paid = max(0, p.federalWithheld) + max(0, p.estimatedPaid)
  const taxedRows = brackets.filter((b) => b.taxed > 0)
  const marginal = taxable <= 0 ? 0 : taxedRows[taxedRows.length - 1].rate
  const effective = gross > 0 ? (due + fica) / gross : 0
  const quarterly = max(0, due - max(0, p.federalWithheld)) / 4

  return {
    grossIncome: gross, adjustedGross: agi, deduction, taxableIncome: taxable, brackets, incomeTaxBeforeCredits: taxBefore,
    childCredit: credit, incomeTax, selfEmploymentTax: seTax, additionalMedicare: addMedicare, employeeFica: fica,
    stateTax, marginalRate: marginal, dueBeforePayments: due, totalPaid: paid, balance: due - paid, effectiveRate: effective, quarterly,
  }
}

export function quarterlyDates(year: number): string[] {
  return [`Apr 15, ${year}`, `Jun 15, ${year}`, `Sep 15, ${year}`, `Jan 15, ${year + 1}`]
}
