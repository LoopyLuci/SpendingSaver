import { db } from '../db/db'
import { startOfMonth, endOfMonth } from 'date-fns'

export async function getBudgetSpending(categoryId: number, period: 'monthly' | 'yearly', startDate: string): Promise<number> {
  const start = period === 'monthly' ? startOfMonth(new Date(startDate)) : new Date(startDate)
  const end = period === 'monthly' ? endOfMonth(new Date(startDate)) : new Date(new Date(startDate).getFullYear(), 11, 31)
  const txs = await db.transactions
    .where('categoryId').equals(categoryId)
    .filter(tx => {
      const txDate = new Date(tx.date)
      return txDate >= start && txDate <= end && tx.amount < 0
    })
    .toArray()
  return txs.reduce((sum, tx) => sum + Math.abs(tx.amount), 0)
}

export async function getNetWorth(): Promise<number> {
  const accounts = await db.accounts.toArray()
  return accounts.reduce((sum, acc) => sum + acc.balance, 0)
}

export async function getSpendingByCategory(): Promise<Array<{ name: string; value: number }>> {
  const txs = await db.transactions.toArray()
  const categories = await db.categories.toArray()
  const catMap = new Map(categories.map(c => [c.id!, c.name]))
  const grouped: Record<string, number> = {}
  for (const tx of txs) {
    if (tx.amount < 0) {
      const name = tx.categoryId ? (catMap.get(tx.categoryId) ?? 'Uncategorized') : 'Uncategorized'
      grouped[name] = (grouped[name] || 0) + Math.abs(tx.amount)
    }
  }
  return Object.entries(grouped).map(([name, value]) => ({ name, value }))
}
