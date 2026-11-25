import { newId } from '../lib/id'
import { TABLES, DEFAULT_CATEGORIES, defaultSettings, emptyDataset, type Dataset, type Row, type Settings, type TableName } from '../domain/types'
import type { Category, Transaction } from '../domain/types'
import { DexieStorage, MemoryStorage, type Storage } from './storage'
import { readLegacyDataset } from './legacy'

export type Changes = Partial<Record<TableName, Row[]>>

export interface Snapshot {
  ready: boolean
  /** Rows that are not soft-deleted: what the UI shows. */
  live: Dataset
  settings: Settings
  storageKind: 'indexeddb' | 'memory'
  /** Set when saving failed (quota, blocked storage). The change is kept in memory only. */
  writeError: string | null
}

const CHANNEL = 'spendingsaver-web-changes'

/**
 * In-memory copy of the data with write-through persistence. All mutations go through `commit`,
 * which stamps `updatedAt`, persists atomically, updates memory, and notifies subscribers and other tabs.
 */
export class DataStore {
  private all: Dataset = emptyDataset()
  private settings: Settings = defaultSettings()
  private snapshot: Snapshot
  private listeners = new Set<() => void>()
  private channel: BroadcastChannel | null = null

  constructor(private storage: Storage, private readonly fallbackReason: string | null = null) {
    this.snapshot = { ready: false, live: emptyDataset(), settings: this.settings, storageKind: storage.kind, writeError: fallbackReason }
    try {
      if (typeof BroadcastChannel !== 'undefined') {
        this.channel = new BroadcastChannel(CHANNEL)
        this.channel.onmessage = () => { void this.reload() }
      }
    } catch { this.channel = null }
  }

  /** Open IndexedDB; if it is unavailable fall back to memory and say why. */
  static async open(): Promise<DataStore> {
    let store: DataStore
    try {
      if (typeof indexedDB === 'undefined') throw new Error('IndexedDB is not available in this browser')
      store = new DataStore(await DexieStorage.open())
    } catch (e) {
      store = new DataStore(new MemoryStorage(), e instanceof Error ? e.message : 'Storage is blocked')
    }
    await store.reload()
    await store.firstRun()
    return store
  }

  subscribe = (fn: () => void) => {
    this.listeners.add(fn)
    return () => { this.listeners.delete(fn) }
  }
  getSnapshot = () => this.snapshot

  get storageKind() { return this.storage.kind }

  private publish() {
    const live = emptyDataset() as unknown as Record<TableName, Row[]>
    for (const t of TABLES) live[t] = (this.all[t] as Row[]).filter((r) => !r.deleted)
    this.snapshot = { ready: true, live: live as unknown as Dataset, settings: this.settings, storageKind: this.storage.kind, writeError: this.snapshot.writeError }
    this.listeners.forEach((l) => l())
  }

  async reload() {
    const { data, settings } = await this.storage.loadAll()
    this.all = data
    this.settings = { ...defaultSettings(), ...settings }
    this.publish()
  }

  /** Everything including tombstones (backup restore and future sync need the full picture). */
  allRows(): Dataset { return this.all }

  private async firstRun() {
    if (!this.settings.seeded) {
      const now = Date.now()
      const cats: Category[] = DEFAULT_CATEGORIES.map((c) => ({ id: newId(), name: c.name, color: c.color, parentId: null, deleted: false, updatedAt: now }))
      const legacy = this.storage.kind === 'indexeddb' ? await readLegacyDataset() : null
      if (legacy) {
        await this.commit({ accounts: legacy.accounts, categories: legacy.categories, transactions: legacy.transactions, budgets: legacy.budgets }, { notify: false })
      } else if (this.all.categories.length === 0) {
        await this.commit({ categories: cats }, { notify: false })
      }
      await this.updateSettings({ seeded: true })
    }
  }

  async commit(changes: Changes, opts: { notify?: boolean } = {}) {
    const now = Date.now()
    const stamped: Changes = {}
    for (const t of Object.keys(changes) as TableName[]) {
      stamped[t] = changes[t]!.map((r) => ({ ...r, updatedAt: now }))
    }
    // Apply to memory first so the UI is never behind; report persistence failures instead of throwing.
    let error: string | null = null
    try {
      await this.storage.write(stamped)
    } catch (e) {
      error = e instanceof Error ? `Could not save: ${e.message}` : 'Could not save your change to browser storage.'
    }
    for (const t of Object.keys(stamped) as TableName[]) {
      const map = new Map((this.all[t] as Row[]).map((r) => [r.id, r]))
      for (const r of stamped[t]!) map.set(r.id, r)
      ;(this.all as unknown as Record<TableName, Row[]>)[t] = [...map.values()]
    }
    this.snapshot = { ...this.snapshot, writeError: this.fallbackReason ?? error }
    if (opts.notify !== false) {
      this.publish()
      try { this.channel?.postMessage('changed') } catch { /* ignore */ }
    }
  }

  async upsert<T extends TableName>(table: T, ...rows: Dataset[T]) { await this.commit({ [table]: rows } as Changes) }

  /** Soft delete: the row stays as a tombstone (deleted=true) so it can be restored and later synced. */
  async softDelete(table: TableName, ...ids: string[]) {
    const set = new Set(ids)
    const rows = (this.all[table] as Row[]).filter((r) => set.has(r.id) && !r.deleted).map((r) => ({ ...r, deleted: true }))
    if (rows.length) await this.commit({ [table]: rows } as Changes)
    return rows.map((r) => r.id)
  }

  async restoreRows(table: TableName, ...ids: string[]) {
    const set = new Set(ids)
    const rows = (this.all[table] as Row[]).filter((r) => set.has(r.id) && r.deleted).map((r) => ({ ...r, deleted: false }))
    if (rows.length) await this.commit({ [table]: rows } as Changes)
  }

  async updateSettings(patch: Partial<Settings>) {
    this.settings = { ...this.settings, ...patch }
    try { await this.storage.saveSettings(this.settings) } catch { /* settings still apply for this session */ }
    this.publish()
    try { this.channel?.postMessage('changed') } catch { /* ignore */ }
  }

  /** Physically delete everything, including settings: the explicit "Delete all data" action. */
  async wipeAll() {
    await this.storage.wipe()
    this.all = emptyDataset()
    this.settings = { ...defaultSettings(), seeded: true, theme: this.settings.theme, accent: this.settings.accent, currency: this.settings.currency }
    await this.storage.saveSettings(this.settings)
    await this.commit({ categories: DEFAULT_CATEGORIES.map((c) => ({ id: newId(), name: c.name, color: c.color, parentId: null, deleted: false, updatedAt: 0 })) })
  }

  close() {
    this.channel?.close()
    this.storage.close()
  }
}

export type { Transaction }
