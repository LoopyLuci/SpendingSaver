import type { Category, Transaction } from './types'

/** Same keyword rules as the Android app's BudgetRepository.autoCategorize, plus a few common merchants and the stem 'grocer' (so "groceries" matches). */
export const RULES: { keywords: string[]; category: string }[] = [
  { keywords: ['salary', 'payroll', 'direct deposit'], category: 'Income' },
  { keywords: ['rent', 'mortgage'], category: 'Housing' },
  { keywords: ['grocery', 'grocer', 'restaurant', 'doordash', 'uber eats'], category: 'Food' },
  { keywords: ['gas', 'uber', 'lyft', 'shell', 'exxon'], category: 'Transportation' },
  { keywords: ['netflix', 'spotify', 'hulu', 'disney'], category: 'Entertainment' },
  { keywords: ['electric', 'water', 'internet', 'comcast'], category: 'Utilities' },
  { keywords: ['doctor', 'pharmacy', 'cvs', 'hospital'], category: 'Healthcare' },
]

const norm = (s: string) => s.trim().toLowerCase()

/**
 * Suggest a category id for a description. Order: (1) the category you used last time for the exact
 * same description, (2) the first keyword rule whose category exists. Returns null when nothing matches.
 */
export function suggestCategory(description: string, categories: Category[], history: Transaction[] = []): string | null {
  const d = norm(description)
  if (!d) return null
  const live = categories.filter((c) => !c.deleted)
  const ids = new Set(live.map((c) => c.id))
  let best: Transaction | null = null
  for (const t of history) {
    if (t.deleted || !t.categoryId || !ids.has(t.categoryId) || norm(t.description) !== d) continue
    if (!best || t.date > best.date || (t.date === best.date && t.createdAt > best.createdAt)) best = t
  }
  if (best) return best.categoryId
  const byName = new Map(live.map((c) => [norm(c.name), c.id]))
  for (const rule of RULES) {
    if (rule.keywords.some((k) => d.includes(k))) {
      const id = byName.get(norm(rule.category))
      if (id) return id
    }
  }
  return null
}

/** Distinct past descriptions (most recent first), for the description suggestion list. */
export function descriptionSuggestions(history: Transaction[], limit = 60): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  const sorted = [...history].filter((t) => !t.deleted).sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : b.createdAt - a.createdAt))
  for (const t of sorted) {
    const k = norm(t.description)
    if (!k || seen.has(k)) continue
    seen.add(k)
    out.push(t.description)
    if (out.length >= limit) break
  }
  return out
}
