import { centsToPlain, parseAmount } from '../lib/money'
import { parseFlexibleDate } from '../lib/dates'
import { newId } from '../lib/id'
import { suggestCategory } from './categorize'
import { MAX_DESCRIPTION, type Account, type Category, type Transaction } from './types'

export const MAX_CSV_BYTES = 5 * 1024 * 1024
export const MAX_CSV_ROWS = 20000

/** RFC 4180 parser: quoted fields, doubled quotes, embedded commas/newlines, CRLF or LF, optional BOM. */
export function parseCsv(input: string): string[][] {
  const text = input.charCodeAt(0) === 0xfeff ? input.slice(1) : input
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let quoted = false
  let i = 0
  const endRow = () => {
    row.push(field)
    field = ''
    if (!(row.length === 1 && row[0] === '')) rows.push(row) // skip blank lines
    row = []
  }
  while (i < text.length) {
    const c = text[i]
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') { field += '"'; i += 2; continue }
        quoted = false
      } else field += c
    } else if (c === '"' && field === '') quoted = true
    else if (c === ',') { row.push(field); field = '' }
    else if (c === '\r') { if (text[i + 1] === '\n') i++; endRow() }
    else if (c === '\n') endRow()
    else field += c
    i++
  }
  if (field !== '' || row.length) endRow()
  return rows
}

const FORMULA_START = /^[=+\-@\t\r]/

/** Spreadsheet formula-injection guard: text cells that would start a formula get a leading apostrophe. */
export function escapeCell(value: string): string {
  const safe = FORMULA_START.test(value) ? `'${value}` : value
  return /[",\r\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe
}

/** Undo `escapeCell`'s apostrophe so an exported file imports back to the same text. */
export function unescapeCell(value: string): string {
  return /^'[=+\-@\t\r]/.test(value) ? value.slice(1) : value
}

export interface ExportRow { date: string; description: string; amountCents: number; account: string; category: string }

export function exportCsv(txs: Transaction[], accounts: Account[], categories: Category[]): string {
  const acc = new Map(accounts.map((a) => [a.id, a.name]))
  const cat = new Map(categories.map((c) => [c.id, c.name]))
  const sorted = [...txs].filter((t) => !t.deleted).sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.createdAt - b.createdAt))
  const lines = ['Date,Description,Amount,Account,Category']
  for (const t of sorted) {
    lines.push([
      t.date,
      escapeCell(t.description),
      centsToPlain(t.amountCents),
      escapeCell(acc.get(t.accountId) ?? ''),
      escapeCell((t.categoryId && cat.get(t.categoryId)) || ''),
    ].join(','))
  }
  return lines.join('\r\n') + '\r\n'
}

export interface CsvSkip { line: number; reason: string }

export interface CsvImportPlan {
  transactions: Transaction[]
  newCategories: Category[]
  skipped: CsvSkip[]
  /** Set when the file as a whole is unusable (no header, too large...). */
  fatal: string | null
}

export interface CsvImportOptions {
  defaultAccountId: string
  accounts: Account[]
  categories: Category[]
  history: Transaction[]
  now?: number
}

function pickColor(i: number): string {
  const palette = ['#009688', '#2196f3', '#4caf50', '#9c27b0', '#ff9800', '#e91e63', '#795548']
  return palette[i % palette.length]
}

/**
 * Turn CSV text into transactions without touching the database. Columns (case-insensitive header):
 * Date, Description, Amount are required; Account and Category are optional. Unknown Account names fall
 * back to the account chosen in the import dialog; unknown Category names create a category; an empty
 * Category is auto-categorized from the description. Bad rows are skipped and reported by line number.
 */
export function planCsvImport(text: string, opts: CsvImportOptions): CsvImportPlan {
  const empty = (fatal: string): CsvImportPlan => ({ transactions: [], newCategories: [], skipped: [], fatal })
  if (text.length > MAX_CSV_BYTES) return empty('That file is too large (limit 5 MB).')
  const rows = parseCsv(text)
  if (!rows.length) return empty('The file is empty.')
  const header = rows[0].map((h) => h.trim().toLowerCase())
  const col = (name: string) => header.indexOf(name)
  const iDate = col('date'), iDesc = col('description'), iAmt = col('amount'), iAcc = col('account'), iCat = col('category')
  if (iDate < 0 || iDesc < 0 || iAmt < 0) return empty('The first row must have the column names Date, Description and Amount (Account and Category are optional).')
  if (rows.length - 1 > MAX_CSV_ROWS) return empty(`Too many rows (limit ${MAX_CSV_ROWS.toLocaleString()}).`)

  const now = opts.now ?? Date.now()
  const accByName = new Map(opts.accounts.filter((a) => !a.deleted).map((a) => [a.name.trim().toLowerCase(), a.id]))
  const catByName = new Map(opts.categories.filter((c) => !c.deleted).map((c) => [c.name.trim().toLowerCase(), c.id]))
  const newCategories: Category[] = []
  const transactions: Transaction[] = []
  const skipped: CsvSkip[] = []
  const cats = [...opts.categories]

  for (let r = 1; r < rows.length; r++) {
    const row = rows[r]
    const line = r + 1
    const dateText = (row[iDate] ?? '').trim()
    const description = unescapeCell((row[iDesc] ?? '').trim()).slice(0, MAX_DESCRIPTION)
    const amountText = row[iAmt] ?? ''
    if (!dateText && !description && !amountText.trim()) continue
    const date = parseFlexibleDate(dateText)
    if (!date) { skipped.push({ line, reason: dateText ? `Unrecognised date "${dateText.slice(0, 20)}"` : 'Missing date' }); continue }
    if (!description) { skipped.push({ line, reason: 'Missing description' }); continue }
    const amountCents = parseAmount(amountText)
    if (amountCents === null) { skipped.push({ line, reason: amountText.trim() ? `Unreadable amount "${amountText.trim().slice(0, 20)}"` : 'Missing amount' }); continue }

    const accName = iAcc >= 0 ? unescapeCell((row[iAcc] ?? '').trim()).toLowerCase() : ''
    const accountId = (accName && accByName.get(accName)) || opts.defaultAccountId

    let categoryId: string | null = null
    const catName = iCat >= 0 ? unescapeCell((row[iCat] ?? '').trim()).slice(0, 120) : ''
    if (catName && catName.toLowerCase() !== 'uncategorized') {
      const key = catName.toLowerCase()
      let id = catByName.get(key)
      if (!id) {
        id = newId()
        const created: Category = { id, name: catName, parentId: null, color: pickColor(newCategories.length), deleted: false, updatedAt: now }
        newCategories.push(created)
        cats.push(created)
        catByName.set(key, id)
      }
      categoryId = id
    } else if (!catName) {
      categoryId = suggestCategory(description, cats, opts.history)
    }
    transactions.push({
      id: newId(), accountId, amountCents, description, categoryId, date, notes: null, createdAt: now, updatedAt: now, deleted: false,
    })
  }
  return { transactions, newCategories, skipped, fatal: null }
}
