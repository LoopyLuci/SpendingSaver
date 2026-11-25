import { describe, expect, it } from 'vitest'
import { defaultTaxProfile, estimateTax, type FilingStatus, type TaxProfile } from './tax'

// These cases are the Android TaxEngineTest cases (SpendingSaverAndroid/.../tax/TaxEngineTest.kt) with identical numbers.
const p = (o: Partial<TaxProfile> = {}): TaxProfile => ({ ...defaultTaxProfile(), ...o })
const near = (a: number, b: number, eps = 0.01) => expect(Math.abs(a - b)).toBeLessThanOrEqual(eps)

describe('tax estimate (parity with Android TaxEngine)', () => {
  it('single filer on 2026 wages', () => {
    const r = estimateTax(p({ wages: 60000 }))
    near(r.taxableIncome, 43900)
    near(r.incomeTax, 5020) // 10% of 12,400 + 12% of 31,500
    expect(r.marginalRate).toBe(0.12)
    near(r.employeeFica, 60000 * 0.0765)
  })
  it('income below the deduction owes nothing', () => {
    const r = estimateTax(p({ wages: 10000 }))
    expect([r.taxableIncome, r.incomeTax, r.marginalRate]).toEqual([0, 0, 0])
  })
  it('married filing jointly uses the 2025 table', () => {
    near(estimateTax(p({ year: 2025, filing: 'MARRIED_JOINT', wages: 100000 })).incomeTax, 7743)
  })
  it('self-employment tax and the half deduction', () => {
    const r = estimateTax(p({ selfEmployment: 50000 }))
    const se = 50000 * 0.9235 * 0.153
    near(r.selfEmploymentTax, se)
    near(r.adjustedGross, 50000 - se / 2)
  })
  it('social security stops at the wage base', () => {
    const r = estimateTax(p({ wages: 300000 }))
    near(r.employeeFica, 0.062 * 184500 + 0.0145 * 300000)
    expect(r.additionalMedicare).toBeGreaterThan(0)
  })
  it('child credit reduces tax but not below zero', () => {
    const r = estimateTax(p({ wages: 60000, children: 2 }))
    near(r.childCredit, 4400)
    near(r.incomeTax, 5020 - 4400)
    expect(estimateTax(p({ wages: 25000, children: 3 })).incomeTax).toBe(0)
  })
  it('itemized overrides the standard deduction', () => {
    near(estimateTax(p({ wages: 100000, useItemized: true, itemized: 30000 })).taxableIncome, 70000)
  })
  it('state tax uses your rate, but no-tax states are zero', () => {
    near(estimateTax(p({ wages: 60000, state: 'CA', stateRatePercent: 5 })).stateTax, 43900 * 0.05)
    expect(estimateTax(p({ wages: 60000, state: 'TX', stateRatePercent: 5 })).stateTax).toBe(0)
  })
  it('balance, refund and quarterly payments', () => {
    near(estimateTax(p({ wages: 60000, federalWithheld: 3000 })).balance, 2020)
    expect(estimateTax(p({ wages: 60000, federalWithheld: 6000 })).balance).toBeLessThan(0)
    expect(estimateTax(p({ wages: 60000, federalWithheld: 9000 })).quarterly).toBe(0)
  })
  it('has bracket tops and standard deductions for every filing status', () => {
    for (const f of ['SINGLE', 'MARRIED_JOINT', 'MARRIED_SEPARATE', 'HEAD_OF_HOUSEHOLD'] as FilingStatus[]) {
      const r = estimateTax(p({ filing: f, wages: 1_000_000 }))
      expect(r.brackets).toHaveLength(7)
      expect(r.marginalRate).toBe(0.37)
    }
  })
})
