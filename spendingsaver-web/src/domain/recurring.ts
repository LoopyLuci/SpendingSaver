import { addDays, addMonths, isValidYmd, type Ymd } from '../lib/dates'
import type { Frequency, Recurring, Transaction } from './types'

export const FREQUENCIES: Frequency[] = ['weekly', 'biweekly', 'monthly', 'quarterly', 'yearly']

export function nextAfter(date: Ymd, frequency: Frequency): Ymd {
  switch (frequency) {
    case 'weekly': return addDays(date, 7)
    case 'biweekly': return addDays(date, 14)
    case 'quarterly': return addMonths(date, 3)
    case 'yearly': return addMonths(date, 12)
    default: return addMonths(date, 1)
  }
}

const MAX_CATCH_UP = 400

/** Every date this item has come due on or before `today` (capped so a very old date cannot flood the ledger). */
export function dueDates(r: Pick<Recurring, 'nextDate' | 'frequency'>, today: Ymd): Ymd[] {
  const out: Ymd[] = []
  if (!isValidYmd(r.nextDate)) return out
  let d = r.nextDate
  while (d <= today && out.length < MAX_CATCH_UP) {
    out.push(d)
    d = nextAfter(d, r.frequency)
  }
  return out
}

/** Deterministic id so posting the same occurrence twice (two tabs, a retry) overwrites rather than duplicates. */
export function occurrenceId(recurringId: string, date: Ymd): string {
  return `rec-${recurringId}-${date}`
}

export interface PostPlan {
  transactions: Transaction[]
  updated: Recurring[]
}

/** Pure planner: which transactions to create and which recurring rows to advance, for everything due by `today`. */
export function planDuePostings(items: Recurring[], today: Ymd, accountIds: Set<string>, now: number): PostPlan {
  const transactions: Transaction[] = []
  const updated: Recurring[] = []
  for (const r of items) {
    if (r.deleted || !r.active || !accountIds.has(r.accountId)) continue
    const dates = dueDates(r, today)
    if (!dates.length) continue
    for (const date of dates) {
      transactions.push({
        id: occurrenceId(r.id, date), accountId: r.accountId, amountCents: r.amountCents, description: r.name,
        categoryId: r.categoryId, date, notes: 'Posted automatically from a recurring item', createdAt: now, updatedAt: now, deleted: false,
      })
    }
    let next = dates[dates.length - 1]
    next = nextAfter(next, r.frequency)
    updated.push({ ...r, nextDate: next })
  }
  return { transactions, updated }
}

/** Approximate monthly cost of an item (for the monthly total). */
export function monthlyEquivalentCents(r: Pick<Recurring, 'amountCents' | 'frequency'>): number {
  const f: Record<Frequency, number> = { weekly: 52 / 12, biweekly: 26 / 12, monthly: 1, quarterly: 1 / 3, yearly: 1 / 12 }
  return Math.round(r.amountCents * f[r.frequency])
}
