import { useState, useEffect, useCallback } from 'react'
import { db, Transaction, Account, Category } from '../db/db'
import { autoCategorize } from '../services/categorizer'
import { exportCSV } from '../services/csv'

export default function Transactions() {
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [editingId, setEditingId] = useState<number | null>(null)
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [form, setForm] = useState({
    accountId: 0,
    amount: 0,
    description: '',
    date: new Date().toISOString().slice(0, 10),
    categoryId: undefined as number | undefined,
    notes: ''
  })

  const load = useCallback(async () => {
    const txs = await db.transactions.orderBy('date').reverse().toArray()
    setTransactions(txs)
    setAccounts(await db.accounts.toArray())
    setCategories(await db.categories.toArray())
  }, [])

  useEffect(() => { load() }, [load])

  const handleAdd = async () => {
    if (!form.accountId || !form.description) return
    const tx: Transaction = {
      accountId: form.accountId,
      amount: form.amount,
      description: form.description,
      date: form.date,
      categoryId: form.categoryId,
      notes: form.notes,
      createdAt: new Date(),
    }
    tx.categoryId = await autoCategorize(tx)
    await db.transactions.add(tx)
    setForm({ accountId: 0, amount: 0, description: '', date: new Date().toISOString().slice(0, 10), categoryId: undefined, notes: '' })
    load()
  }

  const handleUpdate = async (id: number, updates: Partial<Transaction>) => {
    await db.transactions.update(id, updates)
    setEditingId(null)
    load()
  }

  const handleDelete = async (id: number) => {
    await db.transactions.delete(id)
    load()
  }

  const handleBatchCategorize = async (categoryId: number) => {
    for (const id of selectedIds) {
      await db.transactions.update(id, { categoryId })
    }
    setSelectedIds(new Set())
    load()
  }

  const handleExport = async () => {
    const csv = await exportCSV(transactions)
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'spendingsaver_transactions.csv'
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Transactions</h1>
        <button
          onClick={handleExport}
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
        >
          Export CSV
        </button>
      </div>

      {selectedIds.size > 0 && (
        <div className="mb-4 p-3 bg-yellow-100 dark:bg-yellow-800 rounded flex items-center gap-2">
          <span>{selectedIds.size} selected</span>
          <select
            className="border rounded px-2 py-1 dark:bg-gray-700"
            onChange={(e) => {
              if (e.target.value) handleBatchCategorize(Number(e.target.value))
            }}
            defaultValue=""
          >
            <option value="" disabled>Assign category...</option>
            {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <button
            onClick={() => setSelectedIds(new Set())}
            className="ml-auto text-sm underline"
          >
            Clear
          </button>
        </div>
      )}

      <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-4 mb-6">
        <h2 className="font-semibold mb-2">Add Transaction</h2>
        <div className="grid grid-cols-1 md:grid-cols-5 gap-2">
          <input
            type="date"
            className="border rounded p-2 dark:bg-gray-700"
            value={form.date}
            onChange={e => setForm({ ...form, date: e.target.value })}
          />
          <input
            type="text"
            placeholder="Description"
            className="border rounded p-2 dark:bg-gray-700"
            value={form.description}
            onChange={e => setForm({ ...form, description: e.target.value })}
          />
          <input
            type="number"
            step="0.01"
            placeholder="Amount (negative = expense)"
            className="border rounded p-2 dark:bg-gray-700"
            value={form.amount || ''}
            onChange={e => setForm({ ...form, amount: parseFloat(e.target.value) || 0 })}
          />
          <select
            className="border rounded p-2 dark:bg-gray-700"
            value={form.accountId}
            onChange={e => setForm({ ...form, accountId: Number(e.target.value) })}
          >
            <option value={0}>Select account</option>
            {accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
          <select
            className="border rounded p-2 dark:bg-gray-700"
            value={form.categoryId ?? ''}
            onChange={e => setForm({ ...form, categoryId: e.target.value ? Number(e.target.value) : undefined })}
          >
            <option value="">Auto categorize</option>
            {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <input
            type="text"
            placeholder="Notes (optional)"
            className="border rounded p-2 md:col-span-3 dark:bg-gray-700"
            value={form.notes}
            onChange={e => setForm({ ...form, notes: e.target.value })}
          />
          <button
            onClick={handleAdd}
            className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 md:col-span-2"
          >
            Add
          </button>
        </div>
      </div>

      <div className="overflow-x-auto bg-white dark:bg-gray-800 rounded-lg shadow">
        <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          <thead className="bg-gray-50 dark:bg-gray-700">
            <tr>
              <th className="px-4 py-3 text-left">
                <input
                  type="checkbox"
                  onChange={(e) => {
                    if (e.target.checked) setSelectedIds(new Set(transactions.map(t => t.id!)))
                    else setSelectedIds(new Set())
                  }}
                  checked={selectedIds.size === transactions.length && transactions.length > 0}
                />
              </th>
              <th className="px-4 py-3 text-left">Date</th>
              <th className="px-4 py-3 text-left">Description</th>
              <th className="px-4 py-3 text-left">Category</th>
              <th className="px-4 py-3 text-right">Amount</th>
              <th className="px-4 py-3 text-left">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
            {transactions.map(tx => {
              const isEditing = editingId === tx.id
              return (
                <tr key={tx.id} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                  <td className="px-4 py-2">
                    <input
                      type="checkbox"
                      checked={selectedIds.has(tx.id!)}
                      onChange={(e) => {
                        const newSet = new Set(selectedIds)
                        if (e.target.checked) newSet.add(tx.id!)
                        else newSet.delete(tx.id!)
                        setSelectedIds(newSet)
                      }}
                    />
                  </td>
                  <td className="px-4 py-2">{tx.date}</td>
                  <td className="px-4 py-2">{tx.description}</td>
                  <td className="px-4 py-2">
                    {isEditing ? (
                      <select
                        className="border rounded p-1 dark:bg-gray-700"
                        defaultValue={tx.categoryId ?? ''}
                        onChange={(e) => {
                          const catId = e.target.value ? Number(e.target.value) : undefined
                          handleUpdate(tx.id!, { categoryId: catId })
                        }}
                      >
                        <option value="">Uncategorized</option>
                        {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                      </select>
                    ) : (
                      tx.categoryId ? (categories.find(c => c.id === tx.categoryId)?.name ?? 'Unknown') : 'Uncategorized'
                    )}
                  </td>
                  <td className={`px-4 py-2 text-right ${tx.amount < 0 ? 'text-red-600' : 'text-green-600'}`}>
                    {tx.amount < 0 ? '-' : ''}${Math.abs(tx.amount).toFixed(2)}
                  </td>
                  <td className="px-4 py-2">
                    <button
                      onClick={() => setEditingId(isEditing ? null : tx.id!)}
                      className="text-blue-600 hover:underline mr-2"
                    >
                      {isEditing ? 'Save' : 'Edit'}
                    </button>
                    <button
                      onClick={() => handleDelete(tx.id!)}
                      className="text-red-600 hover:underline"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              )
            })}
            {transactions.length === 0 && (
              <tr>
                <td colSpan={6} className="text-center py-4 text-gray-500">No transactions found</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
