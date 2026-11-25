import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { MAX_CSV_ROWS, escapeCell, exportCsv, parseCsv, planCsvImport, unescapeCell } from './csv'
import { InvalidBackupError, parseBackup, planRestore, serializeBackup, toBackup } from './backup'
import { acct, cat, tx } from './helpers'

const fixture = readFileSync(new URL('../test/fixtures/android-backup.json', import.meta.url), 'utf8')

// ---------------------------------------------------------------- CSV
describe('csv parser', () => {
  it('handles quotes, commas, newlines, CRLF, BOM and blank lines', () => {
    expect(parseCsv('﻿a,b\r\n1,"x, ""y"""\r\n\r\n"multi\nline",3\n')).toEqual([['a', 'b'], ['1', 'x, "y"'], ['multi\nline', '3']])
    expect(parseCsv('')).toEqual([])
    expect(parseCsv('a,b')).toEqual([['a', 'b']])
    expect(parseCsv('a,\n')).toEqual([['a', '']])
  })
})

describe('csv import', () => {
  const checking = acct({ id: 'a1', name: 'Checking' })
  const savings = acct({ id: 'a2', name: 'Savings' })
  const cats = [cat('Entertainment', { id: 'ent' }), cat('Food', { id: 'food' })]
  const plan = (text: string) => planCsvImport(text, { defaultAccountId: 'a1', accounts: [checking, savings], categories: cats, history: [], now: 5 })

  it('imports valid rows into the chosen account and auto-categorizes', () => {
    const r = plan('Date,Description,Amount\n2024-01-05,Netflix,-15.99\n2024-01-06,Mystery shop,-3\n')
    expect(r.fatal).toBeNull()
    expect(r.transactions.map((t) => [t.accountId, t.amountCents, t.categoryId, t.date])).toEqual([
      ['a1', -1599, 'ent', '2024-01-05'], ['a1', -300, null, '2024-01-06'],
    ])
  })
  it('strips $ and thousands separators and accepts US dates', () => {
    const r = plan('Date,Description,Amount\n3/4/2026,Salary,"$1,234.50"\n')
    expect(r.transactions[0]).toMatchObject({ amountCents: 123450, date: '2026-03-04' })
  })
  it('reports skipped rows with line numbers', () => {
    const r = plan('Date,Description,Amount\n,NoDate,5\n2024-01-01,,5\n2024-01-01,BadAmt,abc\n2024-02-30,BadDay,1\n2024-01-01,Good,5\n2024-01-01,OnlyTwo\n')
    expect(r.transactions).toHaveLength(1)
    expect(r.skipped.map((s) => s.line)).toEqual([2, 3, 4, 5, 7])
    expect(r.skipped[0].reason).toMatch(/date/i)
  })
  it('rejects files without the required columns, empty files and oversized files', () => {
    expect(plan('Foo,Bar\n1,2').fatal).toMatch(/Date, Description and Amount/)
    expect(plan('').fatal).toMatch(/empty/)
    expect(plan('Date,Description,Amount\n').transactions).toEqual([])
    expect(plan('x'.repeat(6 * 1024 * 1024)).fatal).toMatch(/too large/)
    const many = 'Date,Description,Amount\n' + '2024-01-01,a,1\n'.repeat(MAX_CSV_ROWS + 1)
    expect(plan(many).fatal).toMatch(/Too many rows/)
  })
  it('uses the optional Account and Category columns and creates unknown categories', () => {
    const r = plan('date,description,amount,account,category\n2024-01-01,Movie,-9,savings,Entertainment\n2024-01-02,Thing,-1,Nowhere,Gadgets\n2024-01-03,Other,-1,,Gadgets\n')
    expect(r.transactions.map((t) => t.accountId)).toEqual(['a2', 'a1', 'a1'])
    expect(r.newCategories.map((c) => c.name)).toEqual(['Gadgets'])
    expect(r.transactions[1].categoryId).toBe(r.newCategories[0].id)
    expect(r.transactions[2].categoryId).toBe(r.newCategories[0].id)
    expect(r.transactions[0].categoryId).toBe('ent')
  })
  it('truncates over-long descriptions', () => {
    expect(plan(`Date,Description,Amount\n2024-01-01,${'x'.repeat(500)},1\n`).transactions[0].description).toHaveLength(200)
  })
})

describe('csv export and round trip', () => {
  const a = acct({ id: 'a1', name: 'Main, "primary"' })
  const food = cat('Food', { id: 'food' })
  const txs = [
    tx({ accountId: 'a1', amountCents: -4225, date: '2024-03-04', description: 'Line, "quoted"', categoryId: 'food' }),
    tx({ accountId: 'a1', amountCents: 300, date: '2024-03-01', description: 'Gift' }),
    tx({ accountId: 'a1', amountCents: -100, date: '2024-03-05', description: '=HYPERLINK("http://x","y")' }),
    tx({ accountId: 'a1', amountCents: -1, date: '2024-03-06', description: 'gone', deleted: true }),
  ]
  it('writes a header, sorted rows, no deleted rows, and neutralises spreadsheet formulas', () => {
    const csv = exportCsv(txs, [a], [food])
    const lines = csv.trim().split('\r\n')
    expect(lines[0]).toBe('Date,Description,Amount,Account,Category')
    expect(lines).toHaveLength(4)
    expect(lines[1]).toBe('2024-03-01,Gift,3.00,"Main, ""primary""",')
    expect(lines[2]).toContain('"Line, ""quoted""",-42.25')
    expect(lines[3]).toContain('\'=HYPERLINK')
    expect(exportCsv([], [], [])).toBe('Date,Description,Amount,Account,Category\r\n')
  })
  it('escape helpers are inverse', () => {
    for (const s of ['=1+1', '+x', '-y', '@z', 'plain', "'quoted", '']) expect(unescapeCell(escapeCell(s).replace(/^"|"$/g, '').replace(/""/g, '"'))).toBe(s)
  })
  it('round-trips through the importer', () => {
    const csv = exportCsv(txs, [a], [food])
    const r = planCsvImport(csv, { defaultAccountId: 'zzz', accounts: [a], categories: [food], history: [], now: 1 })
    expect(r.skipped).toEqual([])
    const norm = (t: { accountId: string; amountCents: number; date: string; description: string; categoryId: string | null }) =>
      `${t.accountId}|${t.amountCents}|${t.date}|${t.description}|${t.categoryId}`
    expect(r.transactions.map(norm).sort()).toEqual(txs.filter((t) => !t.deleted).map(norm).sort())
  })
})

// ---------------------------------------------------------------- Backup
describe('backup: reading files written by the Android and desktop apps', () => {
  it('parses the sample Android-format backup', () => {
    const p = parseBackup(fixture)
    expect(p.data.accounts.map((a) => [a.name, a.type, a.balanceCents, a.currency])).toEqual([
      ['Everyday Checking', 'checking', 284520, 'USD'], ['Vacation', 'savings', 250050, 'USD'], ['Visa', 'credit card', -64035, 'EUR'],
    ])
    expect(p.data.categories[1]).toMatchObject({ name: 'Dining out', parentId: 'c0000000-0000-4000-8000-000000000001', color: null })
    expect(p.data.transactions[0]).toMatchObject({ amountCents: -4000, date: '2026-11-24', categoryId: 'c0000000-0000-4000-8000-000000000001', notes: null })
    expect(p.data.transactions[1]).toMatchObject({ description: 'Payroll, Acme "Corp"', notes: 'line one\nline two', categoryId: null })
    expect(p.data.transactions[2]).toMatchObject({ amountCents: -7, description: 'Fee 🙂' })
    expect(p.data.budgets.map((b) => [b.period, b.amountCents])).toEqual([['monthly', 45000], ['yearly', 120000]])
    expect(p.exportedAt).toBe(1763990400000)
    expect(p.danglingRefs).toBe(0)
  })

  it('reads the exact JSON the desktop test uses for an Android backup (missing optional fields)', () => {
    const json = `{"version":1,"exportedAt":5,"accounts":[{"id":"a","name":"Vacation","type":"savings","balance":2500.5,"currency":"USD","deleted":false,"updatedAt":9}],
      "categories":[{"id":"c","name":"Food","updatedAt":1}],
      "transactions":[{"id":"t","accountId":"a","amount":-40.0,"description":"Lunch","categoryId":"c","date":"2026-11-24","createdAt":2,"deleted":false,"updatedAt":3}],
      "budgets":[{"id":"b","categoryId":"c","amount":80.0,"period":"monthly","startDate":"","updatedAt":4}]}`
    const p = parseBackup(json)
    expect(p.data.transactions[0].amountCents).toBe(-4000)
    expect(p.data.categories[0]).toMatchObject({ parentId: null, color: null, deleted: false })
  })

  it('re-exports the same data in the Android field layout and round-trips it unchanged', () => {
    const first = parseBackup(fixture)
    const out = serializeBackup(toBackup(first.data, 1763990400000))
    const raw = JSON.parse(out)
    expect(Object.keys(raw).sort()).toEqual(['accounts', 'budgets', 'categories', 'exportedAt', 'transactions', 'version'])
    expect(Object.keys(raw.accounts[0]).sort()).toEqual(['balance', 'currency', 'deleted', 'id', 'name', 'type', 'updatedAt'])
    expect(Object.keys(raw.transactions[1]).sort()).toEqual(['accountId', 'amount', 'createdAt', 'date', 'deleted', 'description', 'id', 'notes', 'updatedAt'])
    expect(Object.keys(raw.budgets[0]).sort()).toEqual(['amount', 'categoryId', 'deleted', 'id', 'period', 'startDate', 'updatedAt'])
    expect(raw.accounts[0].balance).toBe(2845.2)
    expect(raw.transactions[2].amount).toBe(-0.07)
    const again = parseBackup(out)
    expect(again.data).toEqual(first.data)
  })

  it('exports only active rows', () => {
    const doc = toBackup({ accounts: [acct({ id: 'a' }), acct({ id: 'b', deleted: true })], categories: [], transactions: [tx({ amountCents: -1, date: '2026-01-01', deleted: true })], budgets: [] })
    expect(doc.accounts.map((a) => a.id)).toEqual(['a'])
    expect(doc.transactions).toEqual([])
  })

  it('rejects files that are not complete, supported backups', () => {
    const bad = [
      '', 'not json', '[]', '{}', 'null', '{"accounts":[]}',
      '{"version":2,"accounts":[],"categories":[],"transactions":[],"budgets":[]}',
      '{"version":1,"accounts":[],"categories":[],"transactions":[]}',
      '{"version":1,"accounts":null,"categories":[],"transactions":[],"budgets":[]}',
      '{"version":"1","accounts":[],"categories":[],"transactions":[],"budgets":[]}',
    ]
    for (const json of bad) expect(() => parseBackup(json), json).toThrow(InvalidBackupError)
    expect(parseBackup('{"version":1,"accounts":[],"categories":[],"transactions":[],"budgets":[]}').data.accounts).toEqual([])
  })

  it('validates fields, lengths and ranges', () => {
    const wrap = (k: string, rows: string) => `{"version":1,"accounts":[],"categories":[],"transactions":[],"budgets":[],"${k}":${rows}}`
    const bad = [
      wrap('accounts', '[{"id":"a"}]'),
      wrap('accounts', '[{"id":"","name":"n","type":"t","balance":1,"currency":"USD"}]'),
      wrap('accounts', '[{"id":"a","name":"n","type":"t","balance":"1","currency":"USD"}]'),
      wrap('accounts', '[{"id":"a","name":"n","type":"t","balance":1e300,"currency":"USD"}]'),
      wrap('accounts', `[{"id":"a","name":"${'x'.repeat(500)}","type":"t","balance":1,"currency":"USD"}]`),
      wrap('categories', '[{"id":"c","name":"x","color":"red"}]'),
      wrap('transactions', '[{"id":"t","amount":1}]'),
      wrap('transactions', '[{"id":"t","accountId":"a","amount":1,"description":"d","date":"2026-02-30"}]'),
      wrap('transactions', '[{"id":"t","accountId":"a","amount":1,"description":"d","date":"2026-02-02","notes":5}]'),
      wrap('budgets', '[{"id":"b","categoryId":"c","amount":1,"period":"daily","startDate":""}]'),
      wrap('accounts', '[1]'),
    ]
    for (const json of bad) expect(() => parseBackup(json), json).toThrow(InvalidBackupError)
    const a = '{"id":"a","name":"n","type":"t","balance":1,"currency":"USD"}'
    expect(() => parseBackup(wrap('accounts', `[${a},${a}]`))).toThrow(/duplicate/)
    expect(() => parseBackup('x'.repeat(26 * 1024 * 1024))).toThrow(/too large/)
  })

  it('counts dangling references but still accepts the file', () => {
    const json = '{"version":1,"accounts":[],"categories":[],"budgets":[],"transactions":[{"id":"t","accountId":"nope","amount":1,"description":"d","date":"2026-02-02"}]}'
    expect(parseBackup(json).danglingRefs).toBe(1)
  })

  it('does not execute or keep unexpected content (prototype pollution keys ignored)', () => {
    const json = '{"__proto__":{"polluted":true},"version":1,"accounts":[],"categories":[],"transactions":[],"budgets":[]}'
    parseBackup(json)
    expect(({} as Record<string, unknown>).polluted).toBeUndefined()
  })
})

describe('restore planning (mirrors Android BackupPlanner)', () => {
  const empty = { accounts: [], categories: [], transactions: [], budgets: [] }
  it('restamps restored rows and tombstones active rows missing from the backup', () => {
    const existing = { ...empty, accounts: [acct({ id: 'keep' }), acct({ id: 'drop' }), acct({ id: 'old', deleted: true, updatedAt: 7 })] }
    const restored = { ...empty, accounts: [acct({ id: 'keep', updatedAt: 5 }), acct({ id: 'new' })] }
    const out = planRestore(existing, restored, 100).accounts
    const by = new Map(out.map((a) => [a.id, a]))
    expect([...by.keys()].sort()).toEqual(['drop', 'keep', 'new'])
    expect(by.get('keep')!.updatedAt).toBe(100)
    expect(by.get('drop')).toMatchObject({ deleted: true, updatedAt: 100 })
  })
  it('brings a soft-deleted row back when the backup contains it', () => {
    const out = planRestore({ ...empty, accounts: [acct({ id: 'a', deleted: true })] }, { ...empty, accounts: [acct({ id: 'a' })] }, 9).accounts
    expect(out).toHaveLength(1)
    expect(out[0].deleted).toBe(false)
  })
})
