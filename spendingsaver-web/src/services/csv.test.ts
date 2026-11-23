import { describe, it, expect } from 'vitest'
import Papa from 'papaparse'
import { db } from '../db/db'
import { importCSV, exportCSV } from './csv'

const csvFile = (text: string) => new File([text], 'tx.csv', { type: 'text/csv' })

describe('importCSV', () => {
  it('imports valid rows into the given account and auto-categorizes', async () => {
    const n = await importCSV(csvFile(
      'Date,Description,Amount\n2024-01-05,Netflix,-15.99\n2024-01-06,Mystery shop,-3\n'), 7)
    expect(n).toBe(2)
    const txs = await db.transactions.orderBy('date').toArray()
    expect(txs.map(t => t.accountId)).toEqual([7, 7])
    expect(txs[0].amount).toBe(-15.99)
    const ent = await db.categories.where('name').equals('Entertainment').first()
    expect(txs[0].categoryId).toBe(ent!.id)
    expect(txs[1].categoryId).toBeUndefined()
  })

  it('strips $ and thousands separators from amounts', async () => {
    await importCSV(csvFile('Date,Description,Amount\n2024-01-01,Salary,"$1,234.50"\n'), 1)
    expect((await db.transactions.toArray())[0].amount).toBe(1234.5)
  })

  it('skips rows with missing fields or non-numeric amounts', async () => {
    const n = await importCSV(csvFile(
      'Date,Description,Amount\n,NoDate,5\n2024-01-01,,5\n2024-01-01,BadAmt,abc\n2024-01-01,Good,5\n'), 1)
    expect(n).toBe(1)
    expect(await db.transactions.count()).toBe(1)
  })

  it('returns 0 for empty input and header-only input', async () => {
    expect(await importCSV(csvFile(''), 1)).toBe(0)
    expect(await importCSV(csvFile('Date,Description,Amount\n'), 1)).toBe(0)
    expect(await db.transactions.count()).toBe(0)
  })

  it('imports nothing when required columns are absent', async () => {
    expect(await importCSV(csvFile('Foo,Bar\n1,2\n'), 1)).toBe(0)
  })

  it('skips short rows missing the Amount column', async () => {
    expect(await importCSV(csvFile('Date,Description,Amount\n2024-01-01,OnlyTwo\n'), 1)).toBe(0)
  })

  it('handles quoted descriptions containing commas and quotes', async () => {
    await importCSV(csvFile('Date,Description,Amount\n2024-01-01,"Joe, ""the"" Shop",-2\n'), 1)
    expect((await db.transactions.toArray())[0].description).toBe('Joe, "the" Shop')
  })

  it('skips blank lines and tolerates CRLF line endings', async () => {
    const n = await importCSV(csvFile('Date,Description,Amount\r\n2024-01-01,A,1\r\n\r\n2024-01-02,B,2\r\n'), 1)
    expect(n).toBe(2)
  })

  // Documents current behavior: dates are stored verbatim, not validated.
  it('does not validate date format (stored as given)', async () => {
    await importCSV(csvFile('Date,Description,Amount\nnot-a-date,A,1\n'), 1)
    expect((await db.transactions.toArray())[0].date).toBe('not-a-date')
  })
})

describe('exportCSV', () => {
  it('writes header only for no transactions', async () => {
    expect(await exportCSV([])).toBe('Date,Description,Category,Amount\n')
  })

  it('exports category names, Uncategorized fallback, and escapes quotes', async () => {
    const food = await db.categories.where('name').equals('Food').first()
    const out = await exportCSV([
      { accountId: 1, amount: -9.5, description: 'Cafe "X", Inc', date: '2024-01-01', categoryId: food!.id, createdAt: new Date() },
      { accountId: 1, amount: 3, description: 'Gift', date: '2024-01-02', createdAt: new Date() },
      { accountId: 1, amount: 1, description: 'Orphan', date: '2024-01-03', categoryId: 9999, createdAt: new Date() },
    ])
    const rows = Papa.parse<string[]>(out.trim(), { header: false }).data
    expect(rows[1]).toEqual(['2024-01-01', 'Cafe "X", Inc', 'Food', '-9.5'])
    expect(rows[2][2]).toBe('Uncategorized')
    expect(rows[3][2]).toBe('Uncategorized')
  })

  it('round-trips through importCSV', async () => {
    const tx = { accountId: 1, amount: -42.25, description: 'Line, "quoted"', date: '2024-03-04', createdAt: new Date() }
    const csv = await exportCSV([tx])
    await importCSV(csvFile(csv), 2)
    const [got] = await db.transactions.toArray()
    expect(got).toMatchObject({ accountId: 2, amount: -42.25, description: tx.description, date: '2024-03-04' })
  })
})
