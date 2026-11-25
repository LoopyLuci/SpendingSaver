import { addDays, addMonths, periodWindow, toYmd, parseYmd, type Ymd } from '../lib/dates'
import { newId } from '../lib/id'
import { toCents } from '../lib/money'
import type { Account, Budget, Category, Dataset, Debt, Goal, Recurring, Transaction } from './types'

/** Made-up sample ledger so a new user can look around (and so screenshots contain no personal data). */
export function buildSampleData(categories: Category[], today: Ymd, now = Date.now()): Omit<Dataset, 'categories'> & { categories: Category[] } {
  const cat = (name: string) => categories.find((c) => c.name.toLowerCase() === name.toLowerCase())?.id ?? null
  const mkAcc = (name: string, type: string, balance: number): Account => ({ id: newId(), name, type, balanceCents: toCents(balance), currency: 'USD', deleted: false, updatedAt: now })
  const checking = mkAcc('Everyday Checking', 'Checking', 2845.2)
  const savings = mkAcc('Rainy Day Savings', 'Savings', 12500)
  const card = mkAcc('Travel Card', 'Credit card', -640.35)

  const txs: Transaction[] = []
  const first = periodWindow('monthly', today).from
  const push = (accountId: string, date: Ymd, amount: number, description: string, category: string | null) => {
    if (date > today) return
    txs.push({ id: newId(), accountId, amountCents: toCents(amount), description, categoryId: category ? cat(category) : null, date, notes: null, createdAt: now, updatedAt: now, deleted: false })
  }
  const dayIn = (monthStart: Ymd, d: number) => { const p = parseYmd(monthStart)!; return toYmd(p.y, p.m, Math.min(d, 28)) }
  for (let i = 5; i >= 0; i--) {
    const m = addMonths(first, -i)
    push(checking.id, dayIn(m, 1), 3200, 'Payroll - Acme Corp', 'Income')
    push(checking.id, dayIn(m, 1), -1150, 'Rent', 'Housing')
    push(checking.id, dayIn(m, 4), -(62 + ((i * 7) % 15)), 'Electric company', 'Utilities')
    push(checking.id, dayIn(m, 5), -59.99, 'Home internet', 'Utilities')
    push(card.id, dayIn(m, 6), -15.49, 'Netflix', 'Entertainment')
    push(card.id, dayIn(m, 6), -10.99, 'Spotify', 'Entertainment')
    push(checking.id, dayIn(m, 8), -(84 + ((i * 11) % 30)), 'Green Grocer groceries', 'Food')
    push(checking.id, dayIn(m, 12), -(46 + ((i * 5) % 20)), 'Shell gas', 'Transportation')
    push(card.id, dayIn(m, 14), -(38 + ((i * 9) % 25)), 'Sushi restaurant', 'Food')
    push(checking.id, dayIn(m, 17), -(92 + ((i * 13) % 35)), 'Green Grocer groceries', 'Food')
    push(card.id, dayIn(m, 19), -(24 + ((i * 3) % 12)), 'Cinema night', 'Entertainment')
    push(checking.id, dayIn(m, 22), -(30 + ((i * 17) % 40)), 'Pharmacy', 'Healthcare')
    push(checking.id, dayIn(m, 25), -(41 + ((i * 6) % 18)), 'Corner Cafe', 'Food')
  }
  push(checking.id, addDays(today, -1), -18.75, 'Coffee and bagels', 'Food')

  const mkBudget = (category: string, amount: number): Budget | null => {
    const id = cat(category)
    return id ? { id: newId(), categoryId: id, amountCents: toCents(amount), period: 'monthly', startDate: '', deleted: false, updatedAt: now } : null
  }
  const budgets = [mkBudget('Food', 450), mkBudget('Entertainment', 80), mkBudget('Transportation', 120), mkBudget('Utilities', 150)].filter((b): b is Budget => b !== null)

  const nextMonth = addMonths(first, 1)
  const rec = (name: string, amount: number, accountId: string, category: string | null, frequency: Recurring['frequency'], nextDate: Ymd): Recurring => ({
    id: newId(), name, amountCents: toCents(amount), accountId, categoryId: category ? cat(category) : null, frequency, nextDate, active: true, deleted: false, updatedAt: now,
  })
  const recurring = [
    rec('Rent', -1150, checking.id, 'Housing', 'monthly', nextMonth),
    rec('Payroll - Acme Corp', 3200, checking.id, 'Income', 'monthly', nextMonth),
    rec('Netflix', -15.49, card.id, 'Entertainment', 'monthly', addDays(nextMonth, 5)),
  ]
  const goals: Goal[] = [
    { id: newId(), name: 'Emergency fund', targetCents: toCents(15000), savedCents: toCents(12500), due: addMonths(first, 8), deleted: false, updatedAt: now },
    { id: newId(), name: 'Summer trip', targetCents: toCents(2400), savedCents: toCents(650), due: addMonths(first, 6), deleted: false, updatedAt: now },
  ]
  const debts: Debt[] = [
    { id: newId(), name: 'Travel Card', balanceCents: toCents(640.35), apr: 22.9, minPaymentCents: toCents(35), deleted: false, updatedAt: now },
    { id: newId(), name: 'Car loan', balanceCents: toCents(8200), apr: 6.4, minPaymentCents: toCents(310), deleted: false, updatedAt: now },
  ]
  return { accounts: [checking, savings, card], categories: [], transactions: txs, budgets, recurring, goals, debts }
}
