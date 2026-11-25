import type { Account, Budget, Category, Transaction } from './types'

/** Test and sample helpers for building rows. */
let n = 0
const base = () => ({ id: `id-${++n}`, updatedAt: 1, deleted: false })
export const acct = (o: Partial<Account> = {}): Account => ({ ...base(), name: 'Checking', type: 'checking', balanceCents: 0, currency: 'USD', ...o })
export const cat = (name: string, o: Partial<Category> = {}): Category => ({ ...base(), name, parentId: null, color: null, ...o })
export const tx = (o: Partial<Transaction> & { amountCents: number; date: string }): Transaction => ({
  ...base(), accountId: 'a1', description: 'x', categoryId: null, notes: null, createdAt: 1, ...o,
})
export const bud = (o: Partial<Budget> & { categoryId: string; amountCents: number }): Budget => ({ ...base(), period: 'monthly', startDate: '', ...o })
