/** Calendar dates are plain "YYYY-MM-DD" strings (the format the Android and desktop apps store). */

export type Ymd = string

const pad = (n: number) => String(n).padStart(2, '0')

export function toYmd(y: number, m: number, d: number): Ymd {
  return `${String(y).padStart(4, '0')}-${pad(m)}-${pad(d)}`
}

export function isValidYmd(s: string): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s)
  if (!m) return false
  const y = +m[1], mo = +m[2], d = +m[3]
  return y >= 1900 && y <= 2200 && mo >= 1 && mo <= 12 && d >= 1 && d <= daysInMonth(y, mo)
}

export function daysInMonth(y: number, m: number): number {
  return new Date(Date.UTC(y, m, 0)).getUTCDate()
}

export function parseYmd(s: string): { y: number; m: number; d: number } | null {
  if (!isValidYmd(s)) return null
  return { y: +s.slice(0, 4), m: +s.slice(5, 7), d: +s.slice(8, 10) }
}

/** Today in the user's local time zone. */
export function todayYmd(now: Date = new Date()): Ymd {
  return toYmd(now.getFullYear(), now.getMonth() + 1, now.getDate())
}

function utcDate(s: Ymd): Date {
  const p = parseYmd(s)!
  return new Date(Date.UTC(p.y, p.m - 1, p.d))
}

function fromUtc(d: Date): Ymd {
  return toYmd(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate())
}

export function addDays(s: Ymd, days: number): Ymd {
  const d = utcDate(s)
  d.setUTCDate(d.getUTCDate() + days)
  return fromUtc(d)
}

/** Adds whole months, clamping to the last day of a shorter month (31 Jan + 1 month = 28/29 Feb). */
export function addMonths(s: Ymd, months: number): Ymd {
  const p = parseYmd(s)!
  const total = p.y * 12 + (p.m - 1) + months
  const y = Math.floor(total / 12)
  const m = (total % 12 + 12) % 12 + 1
  return toYmd(y, m, Math.min(p.d, daysInMonth(y, m)))
}

/** 0 = Monday ... 6 = Sunday. */
export function weekdayMon0(s: Ymd): number {
  return (utcDate(s).getUTCDay() + 6) % 7
}

export type Period = 'weekly' | 'monthly' | 'yearly'

/** First and last day (inclusive) of the budget period containing `today`. Weekly runs Monday to Sunday. */
export function periodWindow(period: Period, today: Ymd): { from: Ymd; to: Ymd } {
  const p = parseYmd(today)!
  if (period === 'weekly') {
    const from = addDays(today, -weekdayMon0(today))
    return { from, to: addDays(from, 6) }
  }
  if (period === 'yearly') return { from: toYmd(p.y, 1, 1), to: toYmd(p.y, 12, 31) }
  return { from: toYmd(p.y, p.m, 1), to: toYmd(p.y, p.m, daysInMonth(p.y, p.m)) }
}

export function monthKey(s: Ymd): string {
  return s.slice(0, 7)
}

/** The last `n` calendar months ending with the month of `today`, oldest first, as {key:"2026-11", from, to}. */
export function lastMonths(today: Ymd, n: number): { key: string; from: Ymd; to: Ymd }[] {
  const out: { key: string; from: Ymd; to: Ymd }[] = []
  const first = periodWindow('monthly', today).from
  for (let i = n - 1; i >= 0; i--) {
    const from = addMonths(first, -i)
    out.push({ key: monthKey(from), from, to: periodWindow('monthly', from).to })
  }
  return out
}

/** Whole months from `from` to `to` (calendar month difference). */
export function monthsBetween(from: Ymd, to: Ymd): number {
  const a = parseYmd(from)!, b = parseYmd(to)!
  return (b.y - a.y) * 12 + (b.m - a.m)
}

export function formatDate(s: Ymd, options: Intl.DateTimeFormatOptions = { year: 'numeric', month: 'short', day: 'numeric' }): string {
  if (!isValidYmd(s)) return s
  return new Intl.DateTimeFormat(undefined, { ...options, timeZone: 'UTC' }).format(utcDate(s))
}

export function formatMonth(key: string, style: 'short' | 'long' = 'short'): string {
  return new Intl.DateTimeFormat(undefined, { month: style, timeZone: 'UTC' }).format(utcDate(`${key}-01`))
}

/**
 * Parse a date typed or imported in a common format into YYYY-MM-DD.
 * "2026-03-04", "2026-03-04T10:00:00Z", "2026/3/4", and "3/4/2026" (month first, as US bank exports use).
 * Day-first is only assumed when the first number cannot be a month ("25/12/2026").
 */
export function parseFlexibleDate(text: string): Ymd | null {
  const t = text.trim()
  let m = /^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[T\s].*)?$/.exec(t)
  if (m) {
    const s = toYmd(+m[1], +m[2], +m[3])
    return isValidYmd(s) ? s : null
  }
  m = /^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/.exec(t)
  if (m) {
    let mo = +m[1], d = +m[2]
    if (mo > 12 && d <= 12) [mo, d] = [d, mo]
    const s = toYmd(+m[3], mo, d)
    return isValidYmd(s) ? s : null
  }
  return null
}
