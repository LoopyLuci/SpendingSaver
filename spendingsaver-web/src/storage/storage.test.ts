import 'fake-indexeddb/auto'
import Dexie from 'dexie'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { DataStore } from './store'
import { DexieStorage, MemoryStorage, DB_NAME } from './storage'
import { readLegacyDataset, LEGACY_DB_NAME } from './legacy'
import { acct, tx } from '../domain/helpers'

const open = async () => new DataStore(await DexieStorage.open())

beforeEach(async () => {
  await Dexie.delete(DB_NAME)
  await Dexie.delete(LEGACY_DB_NAME)
})
afterEach(async () => {
  await Dexie.delete(DB_NAME)
  await Dexie.delete(LEGACY_DB_NAME)
})

describe('storage layer (Dexie over fake-indexeddb)', () => {
  it('seeds default categories once on first run, not again after reopening', async () => {
    const a = await DataStore.open()
    expect(a.getSnapshot().live.categories.map((c) => c.name)).toContain('Income')
    const n = a.getSnapshot().live.categories.length
    expect(n).toBe(8)
    a.close()
    const b = await DataStore.open()
    expect(b.getSnapshot().live.categories).toHaveLength(n)
    b.close()
  })

  it('persists rows across close/reopen and carries id, updatedAt and deleted', async () => {
    const s = await open()
    await s.reload()
    await s.upsert('accounts', acct({ id: 'a1', name: 'Main', balanceCents: 12345 }))
    await s.upsert('transactions', tx({ id: 't1', accountId: 'a1', amountCents: -250, date: '2026-01-02', description: 'Coffee' }))
    s.close()
    const s2 = await open()
    await s2.reload()
    const snap = s2.getSnapshot().live
    expect(snap.accounts[0]).toMatchObject({ id: 'a1', name: 'Main', balanceCents: 12345, deleted: false })
    expect(snap.accounts[0].updatedAt).toBeGreaterThan(1)
    expect(snap.transactions[0]).toMatchObject({ id: 't1', amountCents: -250 })
    s2.close()
  })

  it('soft delete keeps a tombstone, hides the row from the UI, and can be restored', async () => {
    const s = await open()
    await s.reload()
    await s.upsert('transactions', tx({ id: 't1', accountId: 'a1', amountCents: -1, date: '2026-01-02' }))
    const before = s.allRows().transactions[0].updatedAt
    await new Promise((r) => setTimeout(r, 5))
    await s.softDelete('transactions', 't1')
    expect(s.getSnapshot().live.transactions).toHaveLength(0)
    const tomb = s.allRows().transactions.find((t) => t.id === 't1')!
    expect(tomb.deleted).toBe(true)
    expect(tomb.updatedAt).toBeGreaterThan(before)
    s.close()
    const s2 = await open()
    await s2.reload()
    expect(s2.allRows().transactions.find((t) => t.id === 't1')?.deleted).toBe(true) // survives reload as a tombstone
    await s2.restoreRows('transactions', 't1')
    expect(s2.getSnapshot().live.transactions).toHaveLength(1)
    s2.close()
  })

  it('stores settings and notifies subscribers', async () => {
    const s = await open()
    await s.reload()
    let calls = 0
    s.subscribe(() => calls++)
    await s.updateSettings({ theme: 'dark', accent: 'blue' })
    expect(calls).toBeGreaterThan(0)
    s.close()
    const s2 = await open()
    await s2.reload()
    expect(s2.getSnapshot().settings).toMatchObject({ theme: 'dark', accent: 'blue' })
    s2.close()
  })

  it('wipes all data on request', async () => {
    const s = await open()
    await s.reload()
    await s.upsert('accounts', acct({ id: 'a1' }))
    await s.wipeAll()
    expect(s.getSnapshot().live.accounts).toHaveLength(0)
    expect(s.allRows().accounts).toHaveLength(0)
    s.close()
  })

  it('falls back to memory storage and reports it when IndexedDB is unavailable', async () => {
    const store = new DataStore(new MemoryStorage(), 'blocked')
    await store.reload()
    await store.upsert('accounts', acct({ id: 'a1' }))
    expect(store.getSnapshot().live.accounts).toHaveLength(1)
    expect(store.getSnapshot().storageKind).toBe('memory')
    expect(store.getSnapshot().writeError).toBe('blocked')
    const saved = globalThis.indexedDB
    // @ts-expect-error simulate a browser without IndexedDB
    delete globalThis.indexedDB
    try {
      const s = await DataStore.open()
      expect(s.getSnapshot().storageKind).toBe('memory')
      expect(s.getSnapshot().writeError).toMatch(/IndexedDB/)
      await s.upsert('accounts', acct({ id: 'x' }))
      expect(s.getSnapshot().live.accounts).toHaveLength(1)
    } finally {
      globalThis.indexedDB = saved
    }
  })

  it('reports a failed write without losing the in-memory change', async () => {
    const storage = new MemoryStorage()
    storage.write = async () => { throw new Error('quota exceeded') }
    const s = new DataStore(storage)
    await s.reload()
    await s.upsert('accounts', acct({ id: 'a1' }))
    expect(s.getSnapshot().live.accounts).toHaveLength(1)
    expect(s.getSnapshot().writeError).toMatch(/quota exceeded/)
  })
})

describe('migration from the earlier prototype database', () => {
  it('converts numeric ids and float amounts, and leaves the old database in place', async () => {
    const old = new Dexie(LEGACY_DB_NAME)
    old.version(1).stores({
      accounts: '++id, name, type', categories: '++id, name, parentId',
      transactions: '++id, accountId, categoryId, date, createdAt', budgets: '++id, categoryId, period, startDate',
    })
    await old.table('accounts').add({ name: 'Old checking', type: 'checking', balance: 100.1, currency: 'USD', createdAt: new Date() })
    await old.table('categories').bulkAdd([{ name: 'Food', color: '#f97316' }, { name: 'Other', color: 'bogus' }])
    await old.table('transactions').bulkAdd([
      { accountId: 1, amount: -9.99, description: 'Lunch', categoryId: 1, date: '2024-01-01', createdAt: new Date() },
      { accountId: 99, amount: -1, description: 'orphan', date: '2024-01-01', createdAt: new Date() },
      { accountId: 1, amount: -1, description: 'bad date', date: 'nope', createdAt: new Date() },
    ])
    await old.table('budgets').add({ categoryId: 1, amount: 50, period: 'monthly', startDate: '2024-01-01' })
    old.close()
    const ds = (await readLegacyDataset())!
    expect(ds.accounts[0]).toMatchObject({ name: 'Old checking', balanceCents: 10010 })
    expect(ds.transactions).toHaveLength(1)
    expect(ds.transactions[0]).toMatchObject({ amountCents: -999, categoryId: ds.categories[0].id })
    expect(ds.categories[1].color).toBeNull()
    expect(ds.budgets[0]).toMatchObject({ amountCents: 5000, categoryId: ds.categories[0].id })
    expect(await Dexie.exists(LEGACY_DB_NAME)).toBe(true)

    const store = await DataStore.open() // first run imports it
    expect(store.getSnapshot().live.transactions).toHaveLength(1)
    expect(store.getSnapshot().live.categories).toHaveLength(2) // converted ones, no extra defaults
    store.close()
  })

  it('returns null when there is no old database or only seeded categories', async () => {
    expect(await readLegacyDataset()).toBeNull()
    const old = new Dexie(LEGACY_DB_NAME)
    old.version(1).stores({ categories: '++id, name' })
    await old.table('categories').add({ name: 'Food' })
    old.close()
    expect(await readLegacyDataset()).toBeNull()
  })
})

// ---------------------------------------------------------------- actions over the store
import { applyCsvImport, deleteAccount, deleteCategory, ensureCategory, makeBackupFile, postDueRecurring, prepareCsvImport, restoreBackup } from './actions'
import { parseBackup } from '../domain/backup'

describe('store actions', () => {
  it('creates a category once for free-text names and reuses it case-insensitively', async () => {
    const s = await open(); await s.reload()
    const a = await ensureCategory(s, '  Pet supplies ')
    const b = await ensureCategory(s, 'pet SUPPLIES')
    expect(a).toBe(b)
    expect(await ensureCategory(s, '   ')).toBeNull()
    s.close()
  })

  it('refuses to delete an account that has transactions, and deleting a category uncategorizes them', async () => {
    const s = await open(); await s.reload()
    await s.upsert('accounts', acct({ id: 'a1' }))
    const cid = (await ensureCategory(s, 'Fun'))!
    await s.upsert('transactions', tx({ id: 't1', accountId: 'a1', amountCents: -5, date: '2026-01-01', categoryId: cid }))
    expect(await deleteAccount(s, 'a1')).toMatch(/1 transaction/)
    await deleteCategory(s, cid)
    expect(s.getSnapshot().live.transactions[0].categoryId).toBeNull()
    await s.softDelete('transactions', 't1')
    expect(await deleteAccount(s, 'a1')).toBeNull()
    expect(s.getSnapshot().live.accounts).toHaveLength(0)
    s.close()
  })

  it('posts due recurring items once, even when run twice', async () => {
    const s = await open(); await s.reload()
    await s.upsert('accounts', acct({ id: 'a1' }))
    await s.upsert('recurring', { id: 'r1', name: 'Rent', amountCents: -100000, accountId: 'a1', categoryId: null, frequency: 'monthly', nextDate: '2026-09-15', active: true, deleted: false, updatedAt: 0 })
    expect(await postDueRecurring(s, '2026-11-20')).toBe(3)
    expect(await postDueRecurring(s, '2026-11-20')).toBe(0)
    expect(s.getSnapshot().live.transactions.map((t) => t.date).sort()).toEqual(['2026-09-15', '2026-10-15', '2026-11-15'])
    expect(s.getSnapshot().live.recurring[0].nextDate).toBe('2026-12-15')
    s.close()
  })

  it('imports CSV and restores a backup end to end', async () => {
    const s = await DataStore.open()
    await s.upsert('accounts', acct({ id: 'a1', name: 'Main' }))
    const plan = prepareCsvImport(s, 'Date,Description,Amount\n2026-01-05,Netflix,-15.99\n', 'a1')
    await applyCsvImport(s, plan)
    expect(s.getSnapshot().live.transactions[0]).toMatchObject({ amountCents: -1599 })
    expect(s.getSnapshot().live.transactions[0].categoryId).not.toBeNull() // auto-categorized as Entertainment

    const file = makeBackupFile(s)
    expect(file.name).toMatch(/^spendingsaver-backup-\d{4}-\d{2}-\d{2}\.json$/)
    const parsed = parseBackup(file.text)
    // Add something that is not in the backup, then restore: it must become a tombstone.
    await s.upsert('accounts', acct({ id: 'extra', name: 'Extra' }))
    await restoreBackup(s, parsed)
    expect(s.getSnapshot().live.accounts.map((a) => a.id)).toEqual(['a1'])
    expect(s.allRows().accounts.find((a) => a.id === 'extra')?.deleted).toBe(true)
    expect(s.getSnapshot().live.transactions).toHaveLength(1)
    s.close()
  })
})
