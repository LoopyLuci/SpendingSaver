import { describe, expect, it } from 'vitest'
import { centsToPlain, formatMoney, fromCents, parseAmount, percent, toCents } from './money'
import { addDays, addMonths, isValidYmd, lastMonths, parseFlexibleDate, periodWindow, weekdayMon0 } from './dates'
import { newId } from './id'

describe('money', () => {
  it('converts decimals to integer cents without float drift', () => {
    expect(toCents(0.1 + 0.2)).toBe(30)
    expect(toCents(1.005)).toBe(101)
    expect(toCents(-19.99)).toBe(-1999)
    expect(toCents(2500.5)).toBe(250050)
    expect(fromCents(-1999)).toBe(-19.99)
  })

  it('parses typed amounts in common formats', () => {
    const cases: [string, number | null][] = [
      ['12', 1200], ['12.5', 1250], ['12,5', 1250], ['$40', 4000], ['1,234.50', 123450], ['1.234,50', 123450],
      ['1,234', 123400], ['1,234,567.89', 123456789], ['-3', -300], ['(12.50)', -1250], ['3-', -300], ['+7.1', 710],
      ['  $ 1 000,00 ', 100000], ['0.07', 7], ['.5', 50],
      ['', null], ['abc', null], ['1.234', null], ['1.2.3,4', null], ['12.345,6', 1234560], ['1,2,3', null], ['--5', null], ['1e5', null], ['.', null],
    ]
    for (const [text, expected] of cases) expect(parseAmount(text), text).toBe(expected)
  })

  it('refuses absurd amounts', () => {
    expect(parseAmount('99999999999999')).toBeNull()
  })

  it('formats cents with Intl and falls back for non-ISO currency text', () => {
    expect(formatMoney(123456, 'USD').replace(/\s/g, '')).toMatch(/\$1,234\.56/)
    expect(formatMoney(-5, 'USD')).toMatch(/-.*0\.05/)
    expect(formatMoney(100, 'Zorkmids')).toBe('1.00 Zorkmids')
    expect(centsToPlain(-1205)).toBe('-12.05')
    expect(centsToPlain(5)).toBe('0.05')
  })

  it('percent handles zero and over-100', () => {
    expect(percent(5, 0)).toBe(0)
    expect(percent(150, 100)).toBe(150)
  })
})

describe('dates', () => {
  it('validates calendar dates', () => {
    expect(isValidYmd('2024-02-29')).toBe(true)
    expect(isValidYmd('2026-02-29')).toBe(false)
    expect(isValidYmd('2026-13-01')).toBe(false)
    expect(isValidYmd('26-1-1')).toBe(false)
  })
  it('adds days and clamps months', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28')
    expect(addMonths('2024-01-31', 1)).toBe('2024-02-29')
    expect(addMonths('2026-03-15', -4)).toBe('2025-11-15')
  })
  it('computes period windows like the desktop app (weekly is Monday to Sunday)', () => {
    expect(periodWindow('monthly', '2026-02-10')).toEqual({ from: '2026-02-01', to: '2026-02-28' })
    expect(periodWindow('monthly', '2026-12-31')).toEqual({ from: '2026-12-01', to: '2026-12-31' })
    expect(periodWindow('weekly', '2026-11-24')).toEqual({ from: '2026-11-23', to: '2026-11-29' })
    expect(periodWindow('yearly', '2026-06-01')).toEqual({ from: '2026-01-01', to: '2026-12-31' })
    expect(weekdayMon0('2026-11-23')).toBe(0)
  })
  it('lists the last months oldest first', () => {
    const m = lastMonths('2026-02-10', 3)
    expect(m.map((x) => x.key)).toEqual(['2025-12', '2026-01', '2026-02'])
    expect(m[2].to).toBe('2026-02-28')
  })
  it('parses flexible dates', () => {
    expect(parseFlexibleDate('2026-03-04')).toBe('2026-03-04')
    expect(parseFlexibleDate('2026-03-04T10:00:00Z')).toBe('2026-03-04')
    expect(parseFlexibleDate('3/4/2026')).toBe('2026-03-04')
    expect(parseFlexibleDate('25/12/2026')).toBe('2026-12-25')
    expect(parseFlexibleDate('2026-02-30')).toBeNull()
    expect(parseFlexibleDate('not-a-date')).toBeNull()
  })
})

describe('ids', () => {
  it('makes unique uuid-shaped ids', () => {
    const a = newId(), b = newId()
    expect(a).not.toBe(b)
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  })
})
