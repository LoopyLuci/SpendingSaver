import Papa from 'papaparse'
import { db, Transaction } from '../db/db'
import { autoCategorize } from './categorizer'

export async function importCSV(file: File, accountId: number): Promise<number> {
  const text = await file.text()
  const result = Papa.parse<Record<string, string>>(text, {
    header: true,
    skipEmptyLines: true,
  })

  let imported = 0
  for (const row of result.data) {
    const date = row.Date
    const description = row.Description
    const amountStr = row.Amount?.replace(/[$,]/g, '')
    if (!date || !description || amountStr === undefined) continue

    const amount = parseFloat(amountStr)
    if (isNaN(amount)) continue

    const tx: Transaction = {
      accountId,
      amount,
      description,
      date,
      createdAt: new Date(),
    }

    tx.categoryId = await autoCategorize(tx)
    await db.transactions.add(tx)
    imported++
  }
  return imported
}

export async function exportCSV(transactions: Transaction[]): Promise<string> {
  const categories = await db.categories.toArray()
  const catMap = new Map(categories.map(c => [c.id!, c.name]))
  const header = 'Date,Description,Category,Amount\n'
  const rows = transactions.map(tx => {
    const categoryName = tx.categoryId ? (catMap.get(tx.categoryId) ?? 'Uncategorized') : 'Uncategorized'
    return `${tx.date},"${tx.description.replace(/"/g, '""')}",${categoryName},${tx.amount}`
  }).join('\n')
  return header + rows
}
