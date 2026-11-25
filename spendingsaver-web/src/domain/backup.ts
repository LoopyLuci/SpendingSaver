import { fromCents, toCents } from '../lib/money'
import { isValidYmd } from '../lib/dates'
import type { Account, Budget, Category, Dataset, Row, Transaction } from './types'
import { MAX_DESCRIPTION, MAX_NAME, MAX_NOTES } from './types'

/**
 * JSON backup, the same document the Android app and the desktop app write and read
 * (DataBackupRepository.kt / desktop model.rs): version 1, camelCase fields, decimal amounts,
 * ids are UUID strings, `deleted` and `updatedAt` on each row. Only accounts, categories, transactions and
 * budgets are in it. Recurring items, goals and debts are not part of the shared format.
 */
export const BACKUP_VERSION = 1
export const MAX_BACKUP_BYTES = 25 * 1024 * 1024
const MAX_ROWS = { accounts: 1000, categories: 2000, transactions: 200000, budgets: 5000 }
const MAX_ID = 128
const MAX_ABS = 1e11
const MAX_TIME = 1e15

export class InvalidBackupError extends Error {}

interface BackupAccount { id: string; name: string; type: string; balance: number; currency: string; deleted: boolean; updatedAt: number }
interface BackupCategory { id: string; name: string; parentId?: string | null; color?: string | null; deleted: boolean; updatedAt: number }
interface BackupTransaction {
  id: string; accountId: string; amount: number; description: string; categoryId?: string | null; date: string
  notes?: string | null; createdAt: number; deleted: boolean; updatedAt: number
}
interface BackupBudget { id: string; categoryId: string; amount: number; period: string; startDate: string; deleted: boolean; updatedAt: number }

export interface BackupDocument {
  version: number
  exportedAt: number
  accounts: BackupAccount[]
  categories: BackupCategory[]
  transactions: BackupTransaction[]
  budgets: BackupBudget[]
}

/** Active (non-deleted) rows, exactly what the Android app exports. */
export function toBackup(live: Pick<Dataset, 'accounts' | 'categories' | 'transactions' | 'budgets'>, now = Date.now()): BackupDocument {
  return {
    version: BACKUP_VERSION,
    exportedAt: now,
    accounts: live.accounts.filter((a) => !a.deleted).map((a) => ({
      id: a.id, name: a.name, type: a.type, balance: fromCents(a.balanceCents), currency: a.currency, deleted: false, updatedAt: a.updatedAt,
    })),
    categories: live.categories.filter((c) => !c.deleted).map((c) => {
      const o: BackupCategory = { id: c.id, name: c.name, deleted: false, updatedAt: c.updatedAt }
      if (c.parentId) o.parentId = c.parentId
      if (c.color) o.color = c.color
      return o
    }),
    transactions: live.transactions.filter((t) => !t.deleted).map((t) => {
      const o: BackupTransaction = {
        id: t.id, accountId: t.accountId, amount: fromCents(t.amountCents), description: t.description, date: t.date,
        createdAt: t.createdAt, deleted: false, updatedAt: t.updatedAt,
      }
      if (t.categoryId) o.categoryId = t.categoryId
      if (t.notes) o.notes = t.notes
      return o
    }),
    budgets: live.budgets.filter((b) => !b.deleted).map((b) => ({
      id: b.id, categoryId: b.categoryId, amount: fromCents(b.amountCents), period: b.period, startDate: b.startDate, deleted: false, updatedAt: b.updatedAt,
    })),
  }
}

export function serializeBackup(doc: BackupDocument): string {
  return JSON.stringify(doc, null, 2)
}

const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v)

function str(o: Record<string, unknown>, key: string, what: string, max: number, required = true): string | null {
  const v = o[key]
  if (v === undefined || v === null) {
    if (required) throw new InvalidBackupError(`The backup contains an invalid ${what} (missing "${key}")`)
    return null
  }
  if (typeof v !== 'string') throw new InvalidBackupError(`The backup contains an invalid ${what} ("${key}" must be text)`)
  if (v.length > max) throw new InvalidBackupError(`The backup contains an invalid ${what} ("${key}" is too long)`)
  return v
}

function num(o: Record<string, unknown>, key: string, what: string, required = true, limit = MAX_ABS): number | null {
  const v = o[key]
  if (v === undefined || v === null) {
    if (required) throw new InvalidBackupError(`The backup contains an invalid ${what} (missing "${key}")`)
    return null
  }
  if (typeof v !== 'number' || !Number.isFinite(v) || Math.abs(v) > limit) throw new InvalidBackupError(`The backup contains an invalid ${what} ("${key}" must be a number)`)
  return v
}

function id(o: Record<string, unknown>, what: string): string {
  const v = str(o, 'id', what, MAX_ID)!
  if (!v.trim()) throw new InvalidBackupError(`The backup contains an invalid ${what} (empty id)`)
  return v
}

function stamps(o: Record<string, unknown>, now: number) {
  const updatedAt = num(o, 'updatedAt', 'row', false, MAX_TIME) ?? now
  const deleted = o.deleted === true
  return { updatedAt: Math.trunc(updatedAt), deleted }
}

export interface ParsedBackup {
  data: Pick<Dataset, 'accounts' | 'categories' | 'transactions' | 'budgets'>
  exportedAt: number
  /** Transactions whose account or category id is not in the file (kept, shown as Unknown). */
  danglingRefs: number
}

/** Validate and convert a backup file's text. Throws InvalidBackupError (with a user-readable message) before anything is stored. */
export function parseBackup(text: string, now = Date.now()): ParsedBackup {
  if (text.length > MAX_BACKUP_BYTES) throw new InvalidBackupError('That file is too large to be a SpendingSaver backup.')
  let root: unknown
  try { root = JSON.parse(text) } catch { throw new InvalidBackupError('The file is not valid JSON') }
  if (!isObj(root)) throw new InvalidBackupError('The file is not a SpendingSaver backup')
  if (typeof root.version !== 'number') throw new InvalidBackupError('The file is not a SpendingSaver backup (no version)')
  if (root.version !== BACKUP_VERSION) throw new InvalidBackupError(`Unsupported backup version ${root.version} (this app reads version ${BACKUP_VERSION})`)
  for (const key of ['accounts', 'categories', 'transactions', 'budgets'] as const) {
    const v = root[key]
    if (!Array.isArray(v)) throw new InvalidBackupError(`The backup is incomplete: missing "${key}"`)
    if (v.length > MAX_ROWS[key]) throw new InvalidBackupError(`The backup has too many ${key} (limit ${MAX_ROWS[key].toLocaleString()})`)
  }
  const items = (key: string, what: string) => (root[key] as unknown[]).map((r) => {
    if (!isObj(r)) throw new InvalidBackupError(`The backup contains an invalid ${what}`)
    return r
  })

  const accounts: Account[] = items('accounts', 'account').map((o) => ({
    id: id(o, 'account'), name: str(o, 'name', 'account', MAX_NAME)!, type: str(o, 'type', 'account', 64)!,
    balanceCents: toCents(num(o, 'balance', 'account')!), currency: str(o, 'currency', 'account', 16)!.toUpperCase() || 'USD', ...stamps(o, now),
  }))
  const categories: Category[] = items('categories', 'category').map((o) => {
    const color = str(o, 'color', 'category', 16, false)
    if (color !== null && !/^#[0-9a-fA-F]{6}$/.test(color)) throw new InvalidBackupError('The backup contains an invalid category (colour must look like #rrggbb)')
    return { id: id(o, 'category'), name: str(o, 'name', 'category', MAX_NAME)!, parentId: str(o, 'parentId', 'category', MAX_ID, false), color, ...stamps(o, now) }
  })
  const transactions: Transaction[] = items('transactions', 'transaction').map((o) => {
    const date = str(o, 'date', 'transaction', 10)!
    if (!isValidYmd(date)) throw new InvalidBackupError(`The backup contains an invalid transaction (bad date "${date.slice(0, 12)}")`)
    return {
      id: id(o, 'transaction'), accountId: str(o, 'accountId', 'transaction', MAX_ID)!, amountCents: toCents(num(o, 'amount', 'transaction')!),
      description: str(o, 'description', 'transaction', MAX_DESCRIPTION)!, categoryId: str(o, 'categoryId', 'transaction', MAX_ID, false), date,
      notes: str(o, 'notes', 'transaction', MAX_NOTES, false), createdAt: Math.trunc(num(o, 'createdAt', 'transaction', false, MAX_TIME) ?? now), ...stamps(o, now),
    }
  })
  const budgets: Budget[] = items('budgets', 'budget').map((o) => {
    const period = str(o, 'period', 'budget', 16)!
    if (period !== 'weekly' && period !== 'monthly' && period !== 'yearly') throw new InvalidBackupError(`The backup contains an invalid budget (period "${period.slice(0, 12)}")`)
    return { id: id(o, 'budget'), categoryId: str(o, 'categoryId', 'budget', MAX_ID)!, amountCents: toCents(num(o, 'amount', 'budget')!), period, startDate: str(o, 'startDate', 'budget', 32, false) ?? '', ...stamps(o, now) }
  })

  for (const rows of [accounts, categories, transactions, budgets] as Row[][]) {
    if (new Set(rows.map((r) => r.id)).size !== rows.length) throw new InvalidBackupError('The backup contains duplicate ids')
  }
  const accIds = new Set(accounts.map((a) => a.id))
  const catIds = new Set(categories.map((c) => c.id))
  const dangling = transactions.filter((t) => !accIds.has(t.accountId) || (t.categoryId && !catIds.has(t.categoryId))).length
  return { data: { accounts, categories, transactions, budgets }, exportedAt: Math.trunc(num(root, 'exportedAt', 'backup', false, MAX_TIME) ?? 0), danglingRefs: dangling }
}

type Four = Pick<Dataset, 'accounts' | 'categories' | 'transactions' | 'budgets'>

/**
 * What a restore must write, mirroring the Android BackupPlanner: every backup row restamped "now", plus a
 * tombstone for each currently-active row the backup does not contain. Existing tombstones are left alone.
 */
export function planRestore(existing: Four, restored: Four, now: number): Four {
  const one = <T extends Row>(have: T[], incoming: T[]): T[] => {
    const keep = new Set(incoming.map((r) => r.id))
    const tombstones = have.filter((r) => !keep.has(r.id) && !r.deleted).map((r) => ({ ...r, deleted: true, updatedAt: now }))
    return [...incoming.map((r) => ({ ...r, updatedAt: now })), ...tombstones]
  }
  return {
    accounts: one(existing.accounts, restored.accounts),
    categories: one(existing.categories, restored.categories),
    transactions: one(existing.transactions, restored.transactions),
    budgets: one(existing.budgets, restored.budgets),
  }
}
