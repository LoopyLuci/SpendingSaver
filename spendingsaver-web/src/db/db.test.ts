import { describe, it, expect } from 'vitest'
import { db, seedCategories } from './db'

describe('local persistence (Dexie + fake-indexeddb)', () => {
  it('seeds default categories only once', async () => {
    const first = await db.categories.count()
    expect(first).toBe(8)
    await seedCategories()
    expect(await db.categories.count()).toBe(first)
  })

  it('seeds when the table is empty', async () => {
    await db.categories.clear()
    await seedCategories()
    const names = (await db.categories.toArray()).map(c => c.name)
    expect(names).toContain('Income')
    expect(names).toContain('Other')
  })

  it('stores and reads accounts with auto ids and Date values', async () => {
    const createdAt = new Date('2024-01-02T03:04:05Z')
    const id = await db.accounts.add({ name: 'Main', type: 'checking', balance: 10.5, currency: 'USD', createdAt })
    const got = await db.accounts.get(id)
    expect(got?.name).toBe('Main')
    expect(got?.createdAt).toEqual(createdAt)
  })

  it('queries transactions by indexed accountId and date range', async () => {
    const mk = (accountId: number, date: string) => ({
      accountId, amount: -1, description: 'x', date, createdAt: new Date(),
    })
    await db.transactions.bulkAdd([mk(1, '2024-01-01'), mk(1, '2024-02-01'), mk(2, '2024-01-15')])
    expect(await db.transactions.where('accountId').equals(1).count()).toBe(2)
    const jan = await db.transactions.where('date').between('2024-01-01', '2024-01-31', true, true).count()
    expect(jan).toBe(2)
  })

  it('updates and deletes records', async () => {
    const id = await db.budgets.add({ categoryId: 1, amount: 100, period: 'monthly', startDate: '2024-01-01' })
    await db.budgets.update(id, { amount: 250 })
    expect((await db.budgets.get(id))?.amount).toBe(250)
    await db.budgets.delete(id)
    expect(await db.budgets.get(id)).toBeUndefined()
  })

  it('persists data across a close and reopen of the database', async () => {
    await db.accounts.add({ name: 'Keep', type: 'savings', balance: 1, currency: 'USD', createdAt: new Date() })
    db.close()
    await db.open()
    expect((await db.accounts.toArray()).map(a => a.name)).toEqual(['Keep'])
  })
})
