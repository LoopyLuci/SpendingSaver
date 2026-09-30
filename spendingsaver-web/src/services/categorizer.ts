import { db, Transaction } from '../db/db'

const rules: { keywords: string[]; category: string }[] = [
  { keywords: ['salary', 'payroll', 'direct deposit'], category: 'Income' },
  { keywords: ['rent', 'mortgage'], category: 'Housing' },
  { keywords: ['grocery', 'restaurant', 'doordash', 'uber eats'], category: 'Food' },
  { keywords: ['gas', 'uber', 'lyft', 'shell', 'exxon'], category: 'Transportation' },
  { keywords: ['netflix', 'spotify', 'hulu', 'disney'], category: 'Entertainment' },
  { keywords: ['electric', 'water', 'internet', 'comcast'], category: 'Utilities' },
  { keywords: ['doctor', 'pharmacy', 'cvs', 'hospital'], category: 'Healthcare' },
]

export async function autoCategorize(transaction: Transaction): Promise<number | undefined> {
  const desc = transaction.description.toLowerCase()
  const categories = await db.categories.toArray()
  const catMap = new Map(categories.map(c => [c.name.toLowerCase(), c.id!]))

  for (const rule of rules) {
    if (rule.keywords.some(k => desc.includes(k))) {
      const catId = catMap.get(rule.category.toLowerCase())
      if (catId) return catId
    }
  }
  return undefined
}
