/**
 * Money is always an integer number of hundredths of the currency unit ("cents") in memory and in
 * IndexedDB. Floats only appear at the edges (the JSON backup, which the Android and desktop apps
 * write as decimal numbers) and are converted with rounding.
 */

/** Dollars as written in a backup (a JS number) to integer cents. */
export function toCents(value: number): number {
  return Math.round(value * 100 + (value < 0 ? -1e-9 : 1e-9))
}

/** Integer cents to the decimal number a backup file uses. */
export function fromCents(cents: number): number {
  return cents / 100
}

export const MAX_CENTS = 1_000_000_000_000 // 10 billion units: far beyond any personal ledger

/**
 * Parse user-typed money into integer cents without going through floating point.
 * Accepts "1,234.50", "1.234,50", "12,5", "$40", "(12.50)" (negative), "-3", "3-" and plain numbers.
 * Returns null when the text is not a valid amount or has more than two decimal places.
 */
export function parseAmount(text: string): number | null {
  let t = text.replace(/[\s\u00a0]/g, '')
  if (!t) return null
  let negative = false
  const CURRENCY_PREFIX = /^[\p{Sc}A-Za-z]+/u
  const CURRENCY_SUFFIX = /[\p{Sc}A-Za-z]+$/u
  if (/^\(.*\)$/.test(t)) {
    negative = true
    t = t.slice(1, -1)
  }
  t = t.replace(CURRENCY_PREFIX, '')
  if (t.startsWith('-') || t.startsWith('−')) {
    negative = !negative
    t = t.slice(1)
  } else if (t.endsWith('-')) {
    negative = !negative
    t = t.slice(0, -1)
  } else if (t.startsWith('+')) {
    t = t.slice(1)
  }
  t = t.replace(CURRENCY_PREFIX, '').replace(CURRENCY_SUFFIX, '')
  if (!t || !/^[\d.,]+$/.test(t) || !/\d/.test(t)) return null

  const lastDot = t.lastIndexOf('.')
  const lastComma = t.lastIndexOf(',')
  let normalized: string
  if (lastDot >= 0 && lastComma >= 0) {
    // Both present: the later one is the decimal separator.
    const dec = lastDot > lastComma ? '.' : ','
    const grp = dec === '.' ? ',' : '.'
    const intPart = t.slice(0, t.lastIndexOf(dec))
    const groups = intPart.split(grp)
    if (groups[0].length < 1 || groups[0].length > 3 || !groups.slice(1).every((g) => /^\d{3}$/.test(g))) return null
    normalized = groups.join('') + '.' + t.slice(t.lastIndexOf(dec) + 1)
  } else if (lastComma >= 0 || lastDot >= 0) {
    const sep = lastComma >= 0 ? ',' : '.'
    const parts = t.split(sep)
    const last = parts[parts.length - 1]
    if (parts.length > 2) {
      // Repeated separator: only valid as thousands grouping ("1,234,567").
      if (!parts.slice(1).every((p) => p.length === 3) || parts[0].length < 1 || parts[0].length > 3) return null
      normalized = parts.join('')
    } else if (sep === ',' && last.length === 3 && parts[0].length >= 1 && parts[0].length <= 3) {
      normalized = parts.join('') // "1,234" is one thousand two hundred thirty-four
    } else {
      normalized = parts.join('.') // "12,5" or "12.50"
    }
  } else {
    normalized = t
  }

  if (!/^\d*\.?\d*$/.test(normalized) || normalized === '' || normalized === '.') return null
  const [whole, frac = ''] = normalized.split('.')
  if (frac.length > 2) return null
  if (whole.length > 12) return null
  const cents = Number(whole || '0') * 100 + Number((frac + '00').slice(0, 2))
  if (!Number.isSafeInteger(cents) || cents > MAX_CENTS) return null
  return negative ? -cents : cents
}

/** Plain decimal text for an input field or CSV cell: -1234.5 -> "-1234.50". */
export function centsToPlain(cents: number): string {
  const sign = cents < 0 ? '-' : ''
  const abs = Math.abs(Math.trunc(cents))
  return `${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}

const formatters = new Map<string, Intl.NumberFormat | null>()

function formatterFor(currency: string): Intl.NumberFormat | null {
  const key = currency.toUpperCase()
  if (formatters.has(key)) return formatters.get(key) ?? null
  let f: Intl.NumberFormat | null = null
  try {
    f = new Intl.NumberFormat(undefined, { style: 'currency', currency: key, minimumFractionDigits: 2, maximumFractionDigits: 2 })
  } catch {
    f = null // not an ISO 4217 code (the field accepts free text)
  }
  formatters.set(key, f)
  return f
}

const plainFormatter = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** Locale-aware money text for integer cents, e.g. -123456 -> "-$1,234.56". Unknown currency codes fall back to "1,234.56 XYZ". */
export function formatMoney(cents: number, currency = 'USD'): string {
  const f = formatterFor(currency || 'USD')
  const value = cents / 100
  if (f) return f.format(value)
  return `${plainFormatter.format(value)} ${currency}`
}

/** Compact axis label for chart ticks. */
export function formatCompact(cents: number, currency = 'USD'): string {
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency, notation: 'compact', maximumFractionDigits: 1 }).format(cents / 100)
  } catch {
    return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(cents / 100)
  }
}

/** Percent of part/whole, rounded; 0 when whole is not positive. Can exceed 100. */
export function percent(part: number, whole: number): number {
  return whole > 0 ? Math.round((part / whole) * 100) : 0
}
