import { describe, expect, it } from 'vitest'
import { descriptionSuggestions, suggestCategory } from './categorize'
import { assetsAndLiabilities, budgetStatus, currenciesInUse, monthlyTrend, netWorthCents, spendingByCategory, totalsBetween } from './stats'
import { dueDates, monthlyEquivalentCents, nextAfter, occurrenceId, planDuePostings } from './recurring'
import { goalMonthlyNeedCents, payoffSchedule, simulateStrategy } from './payoff'
import { acct, bud, cat, tx } from './helpers'
import type { Recurring } from './types'

describe('auto-categorize', () => {
  const cats = [cat('Income'), cat('Housing'), cat('Food'), cat('Transportation'), cat('Entertainment'), cat('Utilities'), cat('Healthcare')]
  const id = (n: string) => cats.find((c) => c.name === n)!.id
  it('uses the same keyword rules as the Android app', () => {
    expect(suggestCategory('ACME PAYROLL 11/02', cats)).toBe(id('Income'))
    expect(suggestCategory('Monthly rent', cats)).toBe(id('Housing'))
    expect(suggestCategory('Uber Eats order', cats)).toBe(id('Food'))
    expect(suggestCategory('Green Grocer groceries', cats)).toBe(id('Food')) // 'grocer' is an addition to the Android list
    expect(suggestCategory('Lyft ride', cats)).toBe(id('Transportation'))
    expect(suggestCategory('Netflix.com', cats)).toBe(id('Entertainment'))
    expect(suggestCategory('Comcast internet', cats)).toBe(id('Utilities'))
    expect(suggestCategory('CVS pharmacy', cats)).toBe(id('Healthcare'))
    expect(suggestCategory('Mystery shop', cats)).toBeNull()
    expect(suggestCategory('   ', cats)).toBeNull()
  })
  it('returns nothing when the matching category does not exist', () => {
    expect(suggestCategory('Netflix', [cat('Food')])).toBeNull()
    expect(suggestCategory('Netflix', [cat('Entertainment', { deleted: true })])).toBeNull()
  })
  it('prefers the category you used last time for the same description', () => {
    const custom = cat('Fun money')
    const history = [tx({ amountCents: -1599, date: '2026-01-01', description: 'netflix', categoryId: custom.id })]
    expect(suggestCategory('Netflix', [...cats, custom], history)).toBe(custom.id)
    expect(suggestCategory('Netflix', cats, history)).toBe(id('Entertainment')) // custom category not in list
  })
  it('suggests distinct recent descriptions', () => {
    const h = [tx({ amountCents: -1, date: '2026-01-01', description: 'A' }), tx({ amountCents: -1, date: '2026-02-01', description: 'b' }), tx({ amountCents: -1, date: '2026-03-01', description: 'a' })]
    expect(descriptionSuggestions(h)).toEqual(['a', 'b'])
  })
})

describe('stats and budgets', () => {
  const a1 = acct({ id: 'a1', balanceCents: 100000 })
  const a2 = acct({ id: 'a2', balanceCents: -25050, currency: 'usd' })
  const a3 = acct({ id: 'a3', balanceCents: 5000, currency: 'EUR' })
  const food = cat('Food', { id: 'food' })
  const fun = cat('Fun', { id: 'fun' })
  const txs = [
    tx({ accountId: 'a1', amountCents: -5000, date: '2026-11-10', categoryId: 'food' }),
    tx({ accountId: 'a1', amountCents: -2500, date: '2026-11-11', categoryId: 'food' }),
    tx({ accountId: 'a1', amountCents: -1000, date: '2026-11-12', categoryId: 'fun' }),
    tx({ accountId: 'a1', amountCents: -700, date: '2026-11-12' }),
    tx({ accountId: 'a1', amountCents: 200000, date: '2026-11-01' }),
    tx({ accountId: 'a1', amountCents: -9900, date: '2026-10-31', categoryId: 'food' }),
    tx({ accountId: 'a3', amountCents: -123, date: '2026-11-11', categoryId: 'food' }),
  ]
  it('sums balances per currency (no conversion) and splits assets/liabilities', () => {
    expect(netWorthCents([a1, a2, a3], 'USD')).toBe(74950)
    expect(assetsAndLiabilities([a1, a2, a3], 'USD')).toEqual({ assets: 100000, liabilities: 25050 })
    expect(currenciesInUse([a1, a2, a3])).toEqual(['USD', 'EUR'])
  })
  it('totals a period and groups spending by category, largest first', () => {
    expect(totalsBetween(txs, '2026-11-01', '2026-11-30')).toEqual({ income: 200000, expenses: 9323 })
    const s = spendingByCategory(txs.slice(0, 4), [food, fun], '2026-11-01', '2026-11-30')
    expect(s.map((x) => [x.name, x.cents])).toEqual([['Food', 7500], ['Fun', 1000], ['Uncategorized', 700]])
  })
  it('builds a 6 month trend ending this month', () => {
    const t = monthlyTrend(txs, '2026-11-24', 6)
    expect(t).toHaveLength(6)
    expect(t[5]).toEqual({ key: '2026-11', income: 200000, expenses: 9323 })
    expect(t[4]).toEqual({ key: '2026-10', income: 0, expenses: 9900 })
  })
  it('reports budget progress inside the period and flags overspending', () => {
    const b = bud({ categoryId: 'food', amountCents: 10000 })
    const st = budgetStatus(b, txs, '2026-11-24', [a1, a3], 'USD')
    expect(st).toMatchObject({ spent: 7500, remaining: 2500, pct: 75, over: false, from: '2026-11-01', to: '2026-11-30' })
    const over = budgetStatus(bud({ categoryId: 'food', amountCents: 5000 }), txs, '2026-11-24')
    expect(over).toMatchObject({ spent: 7623, remaining: -2623, over: true, pct: 152 })
    expect(budgetStatus(bud({ categoryId: 'food', amountCents: 5000, period: 'yearly' }), txs, '2026-11-24').spent).toBe(17523)
  })
})

describe('recurring', () => {
  const rec = (o: Partial<Recurring> = {}): Recurring => ({
    id: 'r1', updatedAt: 1, deleted: false, name: 'Rent', amountCents: -115000, accountId: 'a1', categoryId: null,
    frequency: 'monthly', nextDate: '2026-09-31'.replace('31', '30'), active: true, ...o,
  })
  it('advances by frequency, clamping month ends', () => {
    expect(nextAfter('2026-01-31', 'monthly')).toBe('2026-02-28')
    expect(nextAfter('2026-01-01', 'weekly')).toBe('2026-01-08')
    expect(nextAfter('2026-01-01', 'biweekly')).toBe('2026-01-15')
    expect(nextAfter('2026-01-01', 'quarterly')).toBe('2026-04-01')
    expect(nextAfter('2024-02-29', 'yearly')).toBe('2025-02-28')
  })
  it('lists every occurrence due up to today, and none in the future', () => {
    expect(dueDates(rec({ nextDate: '2026-09-15' }), '2026-11-24')).toEqual(['2026-09-15', '2026-10-15', '2026-11-15'])
    expect(dueDates(rec({ nextDate: '2026-12-01' }), '2026-11-24')).toEqual([])
    expect(dueDates(rec({ nextDate: 'garbage' }), '2026-11-24')).toEqual([])
    expect(dueDates(rec({ nextDate: '1990-01-01', frequency: 'weekly' }), '2026-11-24')).toHaveLength(400)
  })
  it('plans postings and the next due date, skipping inactive, deleted and orphaned items', () => {
    const items = [
      rec({ id: 'r1', nextDate: '2026-10-20' }),
      rec({ id: 'r2', active: false, nextDate: '2026-10-20' }),
      rec({ id: 'r3', deleted: true, nextDate: '2026-10-20' }),
      rec({ id: 'r4', accountId: 'gone', nextDate: '2026-10-20' }),
      rec({ id: 'r5', nextDate: '2027-01-01' }),
    ]
    const plan = planDuePostings(items, '2026-11-24', new Set(['a1']), 99)
    expect(plan.transactions.map((t) => [t.id, t.date, t.amountCents])).toEqual([
      [occurrenceId('r1', '2026-10-20'), '2026-10-20', -115000],
      [occurrenceId('r1', '2026-11-20'), '2026-11-20', -115000],
    ])
    expect(plan.updated).toHaveLength(1)
    expect(plan.updated[0].nextDate).toBe('2026-12-20')
    // Re-planning from the advanced item posts nothing: no duplicates.
    expect(planDuePostings(plan.updated, '2026-11-24', new Set(['a1']), 99).transactions).toEqual([])
  })
  it('estimates monthly cost', () => {
    expect(monthlyEquivalentCents({ amountCents: -1200, frequency: 'yearly' })).toBe(-100)
    expect(monthlyEquivalentCents({ amountCents: -1200, frequency: 'weekly' })).toBe(-5200)
  })
})

describe('debt payoff and goals', () => {
  it('matches the desktop calculation for a zero-interest debt', () => {
    expect(payoffSchedule(100000, 0, 10000)).toEqual({ months: 10, interestCents: 0, totalPaidCents: 100000 })
  })
  it('accounts for interest and detects unpayable debts', () => {
    const p = payoffSchedule(500000, 18, 20000)!
    expect(p.months).toBeGreaterThan(30)
    expect(p.interestCents).toBeGreaterThan(0)
    expect(p.totalPaidCents).toBe(500000 + p.interestCents)
    expect(payoffSchedule(500000, 18, 7500)).toBeNull() // interest alone is 7500/month
    expect(payoffSchedule(500000, 18, 0)).toBeNull()
    expect(payoffSchedule(0, 18, 100)).toEqual({ months: 0, interestCents: 0, totalPaidCents: 0 })
  })
  it('avalanche costs no more interest than snowball', () => {
    const debts = [
      { id: 'a', name: 'Card', balanceCents: 300000, apr: 24, minPaymentCents: 9000 },
      { id: 'b', name: 'Store', balanceCents: 80000, apr: 9, minPaymentCents: 3000 },
      { id: 'c', name: 'Loan', balanceCents: 900000, apr: 5, minPaymentCents: 20000 },
    ]
    const av = simulateStrategy(debts, 60000, 'avalanche')
    const sn = simulateStrategy(debts, 60000, 'snowball')
    expect(av.feasible && sn.feasible).toBe(true)
    expect(av.interestCents).toBeLessThanOrEqual(sn.interestCents)
    expect(sn.order[0]).toBe('Store')
    expect(simulateStrategy(debts, 1, 'avalanche').months).toBeGreaterThan(0) // budget is raised to the minimums
  })
  it('computes the monthly need for a goal', () => {
    expect(goalMonthlyNeedCents({ targetCents: 120000, savedCents: 0, due: '2027-11-24' }, '2026-11-24')).toBe(10000)
    expect(goalMonthlyNeedCents({ targetCents: 100, savedCents: 500, due: '2027-11-24' }, '2026-11-24')).toBe(0)
    expect(goalMonthlyNeedCents({ targetCents: 100, savedCents: 0, due: null }, '2026-11-24')).toBeNull()
    expect(goalMonthlyNeedCents({ targetCents: 100, savedCents: 0, due: '2026-01-01' }, '2026-11-24')).toBeNull()
  })
})
