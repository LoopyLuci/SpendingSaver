import { useEffect, useState } from 'react'
import { listBudgets, createBudget, updateBudget, deleteBudget } from '../hooks/useBackend'

export default function Budgets() {
  const [budgets, setBudgets] = useState<any[]>([])
  const [categoryId, setCategoryId] = useState('')
  const [amount, setAmount] = useState('0')
  const [period, setPeriod] = useState('monthly')
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [categories, setCategories] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editAmount, setEditAmount] = useState('0')
  const [editPeriod, setEditPeriod] = useState('monthly')
  const [editCategoryId, setEditCategoryId] = useState('')

  useEffect(() => {
    listBudgets()
      .then(setBudgets)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
    fetch('http://127.0.0.1:8125/api/categories')
      .then(r => r.ok ? r.json() : [])
      .then(setCategories)
      .catch(() => setCategories([]))
  }, [])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await createBudget({ category_id: categoryId, amount: parseFloat(amount), period, start_date: startDate })
      setAmount('0')
      setCategoryId('')
      const updated = await listBudgets()
      setBudgets(updated)
    } catch (e: any) {
      setError(e.message)
    }
  }

  async function saveEdit(id: string) {
    try {
      await updateBudget(id, { amount: parseFloat(editAmount), period: editPeriod, category_id: editCategoryId })
      setEditingId(null)
      const updated = await listBudgets()
      setBudgets(updated)
    } catch (e: any) {
      setError(e.message)
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this budget?')) return
    try {
      await deleteBudget(id)
      const updated = await listBudgets()
      setBudgets(updated)
    } catch (e: any) {
      setError(e.message)
    }
  }

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Add Budget</h3>
        {error && <div className="text-red-600 text-sm mb-3">{error}</div>}
        <form className="space-y-3" onSubmit={submit}>
          <div className="grid gap-3 md:grid-cols-2">
            <select value={categoryId} onChange={e => setCategoryId(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600">
              <option value="">Select category</option>
              {categories.map(c => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
            <input type="number" value={amount} onChange={e => setAmount(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
            <select value={period} onChange={e => setPeriod(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600">
              <option value="monthly">Monthly</option>
              <option value="yearly">Yearly</option>
            </select>
            <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          </div>
          <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded">Save</button>
        </form>
      </section>
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Budgets</h3>
        {loading ? (
          <div className="text-sm text-gray-500">Loading...</div>
        ) : (
          <ul className="space-y-2">
            {budgets.map(b => (
              <li key={b.id} className="flex flex-wrap justify-between items-center gap-2">
                {editingId === b.id ? (
                  <div className="flex flex-wrap items-center gap-2">
                    <select value={editCategoryId} onChange={e => setEditCategoryId(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600">
                      <option value="">Select category</option>
                      {categories.map(c => (
                        <option key={c.id} value={c.id}>{c.name}</option>
                      ))}
                    </select>
                    <input type="number" value={editAmount} onChange={e => setEditAmount(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
                    <select value={editPeriod} onChange={e => setEditPeriod(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600">
                      <option value="monthly">Monthly</option>
                      <option value="yearly">Yearly</option>
                    </select>
                    <button onClick={() => saveEdit(b.id)} className="px-3 py-1 bg-green-600 text-white rounded">Save</button>
                    <button onClick={() => setEditingId(null)} className="px-3 py-1 bg-gray-500 text-white rounded">Cancel</button>
                  </div>
                ) : (
                  <>
                    <span>{b.category_id} • {b.period}</span>
                    <div className="flex items-center gap-3">
                      <span className="font-medium">${b.amount.toFixed(2)}</span>
                      <button onClick={() => { setEditingId(b.id); setEditAmount(String(b.amount)); setEditPeriod(b.period); setEditCategoryId(b.category_id) }} className="text-blue-600">Edit</button>
                      <button onClick={() => handleDelete(b.id)} className="text-red-600">Delete</button>
                    </div>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
