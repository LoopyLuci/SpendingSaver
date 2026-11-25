import { lastMonths, periodWindow, type Period, type Ymd } from '../lib/dates'
import type { Account, Budget, Category, Transaction } from './types'

export interface Totals { income: number; expenses: number }

/** Transactions in `currency` (via their account), inside [from, to] inclusive. No currency conversion is ever done. */
export function inCurrency(txs: Transaction[], accounts: Account[], currency: string): Transaction[] {
  const ok = new Set(accounts.filter((a) => a.currency.toUpperCase() === currency.toUpperCase()).map((a) => a.id))
  return txs.filter((t) => ok.has(t.accountId))
}

export function netWorthCents(accounts: Account[], currency: string): number {
  let total = 0
  for (const a of accounts) if (a.currency.toUpperCase() === currency.toUpperCase()) total += a.balanceCents
  return total
}

export function assetsAndLiabilities(accounts: Account[], currency: string) {
  let assets = 0, liabilities = 0
  for (const a of accounts) {
    if (a.currency.toUpperCase() !== currency.toUpperCase()) continue
    if (a.balanceCents >= 0) assets += a.balanceCents
    else liabilities += -a.balanceCents
  }
  return { assets, liabilities }
}

/** Currencies in use, most accounts first. */
export function currenciesInUse(accounts: Account[]): string[] {
  const n = new Map<string, number>()
  for (const a of accounts) n.set(a.currency.toUpperCase(), (n.get(a.currency.toUpperCase()) ?? 0) + 1)
  return [...n.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([c]) => c)
}

export function totalsBetween(txs: Transaction[], from: Ymd, to: Ymd): Totals {
  const t: Totals = { income: 0, expenses: 0 }
  for (const x of txs) {
    if (x.date < from || x.date > to) continue
    if (x.amountCents > 0) t.income += x.amountCents
    else t.expenses += -x.amountCents
  }
  return t
}

export interface CategorySlice { categoryId: string | null; name: string; color: string; cents: number }

export const UNCATEGORIZED_COLOR = '#9e9e9e'

export function spendingByCategory(txs: Transaction[], categories: Category[], from: Ymd, to: Ymd): CategorySlice[] {
  const byId = new Map(categories.map((c) => [c.id, c]))
  const acc = new Map<string | null, number>()
  for (const t of txs) {
    if (t.amountCents >= 0 || t.date < from || t.date > to) continue
    const key = t.categoryId && byId.has(t.categoryId) ? t.categoryId : null
    acc.set(key, (acc.get(key) ?? 0) - t.amountCents)
  }
  return [...acc.entries()]
    .map(([id, cents]) => {
      const c = id ? byId.get(id) : undefined
      return { categoryId: id, name: c?.name ?? 'Uncategorized', color: c?.color ?? UNCATEGORIZED_COLOR, cents }
    })
    .sort((a, b) => b.cents - a.cents)
}

export interface MonthPoint { key: string; income: number; expenses: number }

export function monthlyTrend(txs: Transaction[], today: Ymd, months = 6): MonthPoint[] {
  return lastMonths(today, months).map((m) => ({ key: m.key, ...totalsBetween(txs, m.from, m.to) }))
}

export interface BudgetStatus {
  budget: Budget
  spent: number
  limit: number
  remaining: number
  /** Unclamped percent used (can exceed 100). */
  pct: number
  over: boolean
  from: Ymd
  to: Ymd
}

export function budgetStatus(budget: Budget, txs: Transaction[], today: Ymd, accounts?: Account[], currency?: string): BudgetStatus {
  const { from, to } = periodWindow(budget.period as Period, today)
  const scoped = accounts && currency ? inCurrency(txs, accounts, currency) : txs
  let spent = 0
  for (const t of scoped) {
    if (t.categoryId === budget.categoryId && t.amountCents < 0 && t.date >= from && t.date <= to) spent += -t.amountCents
  }
  const limit = budget.amountCents
  return { budget, spent, limit, remaining: limit - spent, pct: limit > 0 ? Math.round((spent / limit) * 100) : spent > 0 ? 100 : 0, over: spent > limit, from, to }
}
