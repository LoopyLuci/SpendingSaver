import Dexie, { Table } from 'dexie'

export interface Account {
  id?: number
  name: string
  type: 'checking' | 'savings' | 'credit' | 'investment' | 'other'
  balance: number
  currency: string
  createdAt: Date
}

export interface Category {
  id?: number
  name: string
  parentId?: number
  color?: string
}

export interface Transaction {
  id?: number
  accountId: number
  amount: number          // positive income, negative expense
  description: string
  categoryId?: number
  date: string            // YYYY-MM-DD
  notes?: string
  createdAt: Date
}

export interface Budget {
  id?: number
  categoryId: number
  amount: number
  period: 'monthly' | 'yearly'
  startDate: string       // YYYY-MM-DD
}

class SpendingSaverDB extends Dexie {
  accounts!: Table<Account, number>
  categories!: Table<Category, number>
  transactions!: Table<Transaction, number>
  budgets!: Table<Budget, number>

  constructor() {
    super('SpendingSaverDB')
    this.version(1).stores({
      accounts: '++id, name, type',
      categories: '++id, name, parentId',
      transactions: '++id, accountId, categoryId, date, createdAt',
      budgets: '++id, categoryId, period, startDate'
    })
  }
}

export const db = new SpendingSaverDB()

export async function seedCategories() {
  const count = await db.categories.count()
  if (count === 0) {
    const defaults = [
      { name: 'Income', color: '#22c55e' },
      { name: 'Housing', color: '#3b82f6' },
      { name: 'Food', color: '#f97316' },
      { name: 'Transportation', color: '#8b5cf6' },
      { name: 'Entertainment', color: '#ec4899' },
      { name: 'Utilities', color: '#06b6d4' },
      { name: 'Healthcare', color: '#ef4444' },
      { name: 'Other', color: '#64748b' },
    ]
    await db.categories.bulkAdd(defaults)
  }
}
