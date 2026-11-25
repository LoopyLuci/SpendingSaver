import Dexie from 'dexie'
import { newId } from '../lib/id'
import { toCents } from '../lib/money'
import { isValidYmd } from '../lib/dates'
import type { Account, Budget, Category, Dataset, Transaction } from '../domain/types'
import { emptyDataset } from '../domain/types'

export const LEGACY_DB_NAME = 'SpendingSaverDB'

/**
 * The first browser prototype stored numeric auto-increment ids and float amounts in an IndexedDB
 * database named "SpendingSaverDB". This converts that data to the current model. The old database is
 * left untouched (never deleted), so nothing is lost if the conversion is wrong.
 */
export async function readLegacyDataset(name = LEGACY_DB_NAME): Promise<Dataset | null> {
  if (!(await Dexie.exists(name))) return null
  const old = new Dexie(name)
  try {
    await old.open()
    const has = (t: string) => old.tables.some((x) => x.name === t)
    const out = emptyDataset()
    const now = Date.now()
    const accMap = new Map<number, string>()
    const catMap = new Map<number, string>()
    if (has('accounts')) {
      for (const a of (await old.table('accounts').toArray()) as Array<Record<string, unknown>>) {
        const id = newId()
        accMap.set(Number(a.id), id)
        const row: Account = {
          id, updatedAt: now, deleted: false, name: String(a.name ?? 'Account').slice(0, 120),
          type: String(a.type ?? 'checking'), balanceCents: toCents(Number(a.balance) || 0), currency: String(a.currency ?? 'USD').toUpperCase(),
        }
        out.accounts.push(row)
      }
    }
    if (has('categories')) {
      const rows = (await old.table('categories').toArray()) as Array<Record<string, unknown>>
      for (const c of rows) catMap.set(Number(c.id), newId())
      for (const c of rows) {
        const row: Category = {
          id: catMap.get(Number(c.id))!, updatedAt: now, deleted: false, name: String(c.name ?? 'Category').slice(0, 120),
          parentId: c.parentId != null ? catMap.get(Number(c.parentId)) ?? null : null,
          color: typeof c.color === 'string' && /^#[0-9a-f]{6}$/i.test(c.color) ? c.color : null,
        }
        out.categories.push(row)
      }
    }
    if (has('transactions')) {
      for (const t of (await old.table('transactions').toArray()) as Array<Record<string, unknown>>) {
        const accountId = accMap.get(Number(t.accountId))
        const date = String(t.date ?? '')
        if (!accountId || !isValidYmd(date)) continue
        const row: Transaction = {
          id: newId(), updatedAt: now, deleted: false, accountId, amountCents: toCents(Number(t.amount) || 0),
          description: String(t.description ?? '').slice(0, 200), categoryId: t.categoryId != null ? catMap.get(Number(t.categoryId)) ?? null : null,
          date, notes: typeof t.notes === 'string' ? t.notes.slice(0, 2000) : null,
          createdAt: t.createdAt instanceof Date ? t.createdAt.getTime() : now,
        }
        out.transactions.push(row)
      }
    }
    if (has('budgets')) {
      for (const b of (await old.table('budgets').toArray()) as Array<Record<string, unknown>>) {
        const categoryId = catMap.get(Number(b.categoryId))
        if (!categoryId) continue
        const period = b.period === 'yearly' || b.period === 'weekly' ? b.period : 'monthly'
        const row: Budget = { id: newId(), updatedAt: now, deleted: false, categoryId, amountCents: toCents(Number(b.amount) || 0), period, startDate: String(b.startDate ?? '') }
        out.budgets.push(row)
      }
    }
    const total = out.accounts.length + out.transactions.length + out.budgets.length
    return total > 0 ? out : null
  } catch {
    return null
  } finally {
    old.close()
  }
}
