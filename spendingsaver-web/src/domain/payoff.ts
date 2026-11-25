import { addMonths, type Ymd } from '../lib/dates'
import type { Goal } from './types'
import { monthsBetween, parseYmd } from '../lib/dates'

export interface Payoff { months: number; interestCents: number; totalPaidCents: number }

const MAX_MONTHS = 1200

/**
 * Month-by-month schedule in integer cents: interest each month is round(balance * apr / 12).
 * Returns null when the payment never covers the interest (or is not positive).
 */
export function payoffSchedule(balanceCents: number, aprPercent: number, paymentCents: number): Payoff | null {
  if (balanceCents <= 0) return { months: 0, interestCents: 0, totalPaidCents: 0 }
  if (paymentCents <= 0) return null
  const monthlyRate = aprPercent / 100 / 12
  if (monthlyRate > 0 && paymentCents <= Math.round(balanceCents * monthlyRate)) return null
  let b = balanceCents, months = 0, interest = 0, paid = 0
  while (b > 0 && months < MAX_MONTHS) {
    const i = Math.round(b * monthlyRate)
    interest += i
    const pay = Math.min(paymentCents, b + i)
    paid += pay
    b = b + i - pay
    months++
  }
  return b > 0 ? null : { months, interestCents: interest, totalPaidCents: paid }
}

export function payoffDate(today: Ymd, months: number): Ymd {
  return addMonths(today, months)
}

export type Strategy = 'avalanche' | 'snowball'

export interface DebtInput { id: string; name: string; balanceCents: number; apr: number; minPaymentCents: number }
export interface StrategyResult { months: number; interestCents: number; order: string[]; feasible: boolean }

/**
 * Pay all minimums, then put every spare cent of `monthlyBudgetCents` on the target debt
 * (avalanche: highest APR first; snowball: smallest balance first). Freed minimums roll over.
 */
export function simulateStrategy(debts: DebtInput[], monthlyBudgetCents: number, strategy: Strategy): StrategyResult {
  const state = debts.filter((d) => d.balanceCents > 0).map((d) => ({ ...d, b: d.balanceCents }))
  const minTotal = state.reduce((s, d) => s + d.minPaymentCents, 0)
  const budget = Math.max(monthlyBudgetCents, minTotal)
  const order: string[] = []
  let months = 0, interest = 0
  while (state.some((d) => d.b > 0) && months < MAX_MONTHS) {
    months++
    for (const d of state) if (d.b > 0) { const i = Math.round(d.b * d.apr / 1200); d.b += i; interest += i }
    let pool = budget
    for (const d of state) if (d.b > 0) { const p = Math.min(d.minPaymentCents, d.b); d.b -= p; pool -= p }
    const targets = state.filter((d) => d.b > 0).sort((a, b) => (strategy === 'avalanche' ? b.apr - a.apr || a.b - b.b : a.b - b.b || b.apr - a.apr))
    for (const d of targets) {
      if (pool <= 0) break
      const p = Math.min(pool, d.b)
      d.b -= p
      pool -= p
    }
    for (const d of state) if (d.b === 0 && !order.includes(d.name)) order.push(d.name)
  }
  const feasible = state.every((d) => d.b <= 0)
  return { months, interestCents: interest, order, feasible }
}

/** Monthly amount needed to reach a goal by its due date (null if no date, or the date has passed). */
export function goalMonthlyNeedCents(g: Pick<Goal, 'targetCents' | 'savedCents' | 'due'>, today: Ymd): number | null {
  if (!g.due || !parseYmd(g.due) || g.due <= today) return null
  const months = Math.max(1, monthsBetween(today, g.due))
  return Math.ceil(Math.max(0, g.targetCents - g.savedCents) / months)
}
