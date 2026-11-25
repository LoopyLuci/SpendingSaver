import { newId } from '../lib/id'
import { todayYmd } from '../lib/dates'
import { COLORS, type Account, type Category, type Transaction } from '../domain/types'
import { planDuePostings } from '../domain/recurring'
import { planCsvImport, type CsvImportPlan } from '../domain/csv'
import { planRestore, toBackup, serializeBackup, type ParsedBackup } from '../domain/backup'
import { buildSampleData } from '../domain/sample'
import type { DataStore } from './store'

/** Find a category by name (case-insensitive) or create it. Used by the free-text category fields. */
export async function ensureCategory(store: DataStore, name: string): Promise<string | null> {
  const clean = name.trim().slice(0, 120)
  if (!clean) return null
  const live = store.getSnapshot().live.categories
  const hit = live.find((c) => c.name.trim().toLowerCase() === clean.toLowerCase())
  if (hit) return hit.id
  const created: Category = { id: newId(), name: clean, parentId: null, color: COLORS[live.length % COLORS.length].value, deleted: false, updatedAt: 0 }
  await store.upsert('categories', created)
  return created.id
}

/** Soft-delete a category, un-categorize its transactions and remove its budgets, in one write. */
export async function deleteCategory(store: DataStore, id: string): Promise<void> {
  const snap = store.getSnapshot().live
  const cat = snap.categories.find((c) => c.id === id)
  if (!cat) return
  await store.commit({
    categories: [{ ...cat, deleted: true }, ...snap.categories.filter((c) => c.parentId === id).map((c) => ({ ...c, parentId: null }))],
    transactions: snap.transactions.filter((t) => t.categoryId === id).map((t) => ({ ...t, categoryId: null })),
    budgets: snap.budgets.filter((b) => b.categoryId === id).map((b) => ({ ...b, deleted: true })),
    recurring: snap.recurring.filter((r) => r.categoryId === id).map((r) => ({ ...r, categoryId: null })),
  })
}

/** An account with live transactions cannot be deleted (same rule as the desktop app). Returns an error message or null. */
export async function deleteAccount(store: DataStore, id: string): Promise<string | null> {
  const snap = store.getSnapshot().live
  const n = snap.transactions.filter((t) => t.accountId === id).length
  if (n > 0) return `This account still has ${n} transaction${n === 1 ? '' : 's'}. Delete or move them first.`
  const rec = snap.recurring.filter((r) => r.accountId === id)
  await store.commit({
    accounts: snap.accounts.filter((a) => a.id === id).map((a) => ({ ...a, deleted: true })),
    recurring: rec.map((r) => ({ ...r, deleted: true })),
  })
  return null
}

/** Post every recurring item that is due. Idempotent: occurrence ids are deterministic, so two tabs cannot double-post. */
export async function postDueRecurring(store: DataStore, today = todayYmd()): Promise<number> {
  const snap = store.getSnapshot().live
  const plan = planDuePostings(snap.recurring, today, new Set(snap.accounts.map((a) => a.id)), Date.now())
  if (!plan.transactions.length) return 0
  const existing = new Set(store.allRows().transactions.filter((t) => !t.deleted).map((t) => t.id))
  const fresh = plan.transactions.filter((t) => !existing.has(t.id))
  await store.commit({ transactions: fresh, recurring: plan.updated })
  return fresh.length
}

export async function applyCsvImport(store: DataStore, plan: CsvImportPlan): Promise<void> {
  if (plan.fatal) return
  await store.commit({ categories: plan.newCategories, transactions: plan.transactions })
}

export function prepareCsvImport(store: DataStore, text: string, defaultAccountId: string): CsvImportPlan {
  const snap = store.getSnapshot().live
  return planCsvImport(text, { defaultAccountId, accounts: snap.accounts, categories: snap.categories, history: snap.transactions })
}

/** Replace the active accounts/categories/transactions/budgets with a validated backup. Missing rows become tombstones. */
export async function restoreBackup(store: DataStore, parsed: ParsedBackup): Promise<void> {
  const now = Date.now()
  const plan = planRestore(store.allRows(), parsed.data, now)
  await store.commit({ accounts: plan.accounts, categories: plan.categories, transactions: plan.transactions, budgets: plan.budgets })
}

export function makeBackupFile(store: DataStore): { name: string; text: string } {
  const text = serializeBackup(toBackup(store.getSnapshot().live))
  return { name: `spendingsaver-backup-${todayYmd()}.json`, text }
}

export async function loadSampleData(store: DataStore): Promise<void> {
  const snap = store.getSnapshot().live
  const s = buildSampleData(snap.categories, todayYmd())
  await store.commit({ accounts: s.accounts, transactions: s.transactions, budgets: s.budgets, recurring: s.recurring, goals: s.goals, debts: s.debts })
}

export type { Account, Transaction }
