import 'fake-indexeddb/auto'
import { beforeEach } from 'vitest'
import { db, seedCategories } from '../db/db'

beforeEach(async () => {
  await Promise.all(db.tables.map(t => t.clear()))
  await seedCategories()
})
