import Dexie, { type Table } from 'dexie'
import { TABLES, emptyDataset, type Dataset, type Row, type Settings, type TableName, defaultSettings } from '../domain/types'

/** What the data store needs from a persistence backend. Rows include soft-deleted tombstones. */
export interface Storage {
  readonly kind: 'indexeddb' | 'memory'
  loadAll(): Promise<{ data: Dataset; settings: Settings }>
  /** Write rows (insert or replace by id) across tables in one atomic transaction. */
  write(changes: Partial<Record<TableName, Row[]>>): Promise<void>
  saveSettings(settings: Settings): Promise<void>
  /** Physically remove everything (the explicit "Delete all data" action). */
  wipe(): Promise<void>
  close(): void
}

export const DB_NAME = 'spendingsaver-web'

interface SettingRow { key: string; value: unknown }

export class SpendingSaverDexie extends Dexie {
  accounts!: Table<Row, string>
  categories!: Table<Row, string>
  transactions!: Table<Row, string>
  budgets!: Table<Row, string>
  recurring!: Table<Row, string>
  goals!: Table<Row, string>
  debts!: Table<Row, string>
  settings!: Table<SettingRow, string>

  constructor(name = DB_NAME) {
    super(name)
    // `updatedAt` is indexed on every table so a future sync can ask "what changed since X" cheaply.
    this.version(1).stores({
      accounts: 'id, updatedAt',
      categories: 'id, updatedAt',
      transactions: 'id, accountId, date, updatedAt',
      budgets: 'id, categoryId, updatedAt',
      recurring: 'id, updatedAt',
      goals: 'id, updatedAt',
      debts: 'id, updatedAt',
      settings: 'key',
    })
  }
}

export class DexieStorage implements Storage {
  readonly kind = 'indexeddb' as const
  constructor(readonly db: SpendingSaverDexie = new SpendingSaverDexie()) {}

  static async open(name?: string): Promise<DexieStorage> {
    const s = new DexieStorage(new SpendingSaverDexie(name))
    await s.db.open()
    return s
  }

  async loadAll() {
    const data = emptyDataset() as unknown as Record<TableName, Row[]>
    const settingRows = await this.db.transaction('r', this.db.tables, async () => {
      for (const t of TABLES) data[t] = await (this.db as unknown as Record<TableName, Table<Row, string>>)[t].toArray()
      return this.db.settings.toArray()
    })
    const settings: Record<string, unknown> = { ...defaultSettings() }
    for (const r of settingRows) settings[r.key] = r.value
    return { data: data as unknown as Dataset, settings: settings as unknown as Settings }
  }

  async write(changes: Partial<Record<TableName, Row[]>>) {
    const tables = Object.keys(changes) as TableName[]
    if (!tables.length) return
    const handles = tables.map((t) => (this.db as unknown as Record<TableName, Table<Row, string>>)[t])
    await this.db.transaction('rw', handles, async () => {
      for (const t of tables) await (this.db as unknown as Record<TableName, Table<Row, string>>)[t].bulkPut(changes[t]!)
    })
  }

  async saveSettings(settings: Settings) {
    await this.db.settings.bulkPut(Object.entries(settings).map(([key, value]) => ({ key, value })))
  }

  async wipe() {
    await this.db.transaction('rw', this.db.tables, async () => {
      for (const t of this.db.tables) await t.clear()
    })
  }

  close() {
    this.db.close()
  }
}

/** Fallback when IndexedDB is unavailable (some private modes, blocked storage): works, but nothing survives closing the tab. */
export class MemoryStorage implements Storage {
  readonly kind = 'memory' as const
  private data = emptyDataset() as unknown as Record<TableName, Map<string, Row>>
  private settings: Settings = defaultSettings()
  constructor() {
    for (const t of TABLES) this.data[t] = new Map()
  }
  async loadAll() {
    const out = emptyDataset() as unknown as Record<TableName, Row[]>
    for (const t of TABLES) out[t] = [...this.data[t].values()]
    return { data: out as unknown as Dataset, settings: { ...this.settings } }
  }
  async write(changes: Partial<Record<TableName, Row[]>>) {
    for (const t of Object.keys(changes) as TableName[]) for (const r of changes[t]!) this.data[t].set(r.id, r)
  }
  async saveSettings(settings: Settings) {
    this.settings = { ...settings }
  }
  async wipe() {
    for (const t of TABLES) this.data[t].clear()
  }
  close() {}
}
