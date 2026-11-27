import { readFileSync } from 'node:fs'
import { expect, it } from 'vitest'
import { parseBackup } from './backup'

// A backup exported by the real Android app (release 1.1.2, Nokia 7.2) rather than one written by hand.
it('restores a backup exported by the real Android app', () => {
  const text = readFileSync(new URL('../test/fixtures/real-nokia-backup.json', import.meta.url), 'utf8')
  const b = parseBackup(text)
  expect(b.danglingRefs).toBe(0)
  expect(b.data.accounts.map((a) => [a.name, a.type, a.balanceCents])).toEqual([['Everyday Checking', 'checking', 125050]])
  expect(b.data.transactions.map((t) => [t.description, t.amountCents, t.date])).toEqual([
    ['Grocery Store', -5420, '2026-11-27'],
    ['Paycheck', 200000, '2026-11-27'],
  ])
})
