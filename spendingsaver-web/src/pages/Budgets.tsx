import { useState, useEffect } from 'react'
import { db, Budget, Category } from '../db/db'
import { getBudgetSpending } from '../services/budget'

export default function Budgets() {
  const [budgets, setBudgets] = useState<Budget[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [spendingMap, setSpendingMap] = useState<Record<number, number>>({})
  const [form, setForm] = useState({ categoryId: 0, amount: 0, period: 'monthly' as 'monthly' | 'yearly', startDate: new Date().toISOString().slice(0,10) })

  const load = async () => {
    const b = await db.budgets.toArray()
    setBudgets(b)
    setCategories(await db.categories.toArray())
    const sp: Record<number, number> = {}
    for (const budget of b) {
      sp[budget.id!] = await getBudgetSpending(budget.categoryId, budget.period, budget.startDate)
    }
    setSpendingMap(sp)
  }

  useEffect(() => { load() }, [])

  const handleAdd = async () => {
    if (!form.categoryId || form.amount <= 0) return
    await db.budgets.add({
      categoryId: form.categoryId,
      amount: form.amount,
      period: form.period,
      startDate: form.startDate,
    })
    setForm({ ...form, categoryId: 0, amount: 0 })
    load()
  }

  const handleDelete = async (id: number) => {
    await db.budgets.delete(id)
    load()
  }

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">Budgets</h1>

      <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-4 mb-6">
        <h2 className="font-semibold mb-2">Create Budget</h2>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
          <select
            className="border rounded p-2 dark:bg-gray-700"
            value={form.categoryId}
            onChange={e => setForm({ ...form, categoryId: Number(e.target.value) })}
          >
            <option value={0}>Select category</option>
            {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <input
            type="number"
            step="0.01"
            placeholder="Amount"
            className="border rounded p-2 dark:bg-gray-700"
            value={form.amount || ''}
            onChange={e => setForm({ ...form, amount: parseFloat(e.target.value) || 0 })}
          />
          <select
            className="border rounded p-2 dark:bg-gray-700"
            value={form.period}
            onChange={e => setForm({ ...form, period: e.target.value as 'monthly' | 'yearly' })}
          >
            <option value="monthly">Monthly</option>
            <option value="yearly">Yearly</option>
          </select>
          <input
            type="date"
            className="border rounded p-2 dark:bg-gray-700"
            value={form.startDate}
            onChange={e => setForm({ ...form, startDate: e.target.value })}
          />
          <button
            onClick={handleAdd}
            className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700"
          >
            Add Budget
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {budgets.map(budget => {
          const cat = categories.find(c => c.id === budget.categoryId)
          const spent = spendingMap[budget.id!] ?? 0
          const percentage = budget.amount > 0 ? (spent / budget.amount) * 100 : 0
          return (
            <div key={budget.id} className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
              <div className="flex justify-between items-start">
                <div>
                  <h3 className="font-semibold">{cat?.name ?? 'Unknown'}</h3>
                  <p className="text-sm text-gray-500">{budget.period} starting {budget.startDate}</p>
                </div>
                <button onClick={() => handleDelete(budget.id!)} className="text-red-600 hover:underline">Delete</button>
              </div>
              <div className="mt-3">
                <div className="flex justify-between text-sm">
                  <span>${spent.toFixed(2)} spent</span>
                  <span>${budget.amount.toFixed(2)}</span>
                </div>
                <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2 mt-1">
                  <div
                    className={`h-2 rounded-full ${percentage > 100 ? 'bg-red-500' : percentage > 80 ? 'bg-yellow-500' : 'bg-green-500'}`}
                    style={{ width: `${Math.min(percentage, 100)}%` }}
                  />
                </div>
                <p className="text-xs text-gray-500 mt-1">{percentage.toFixed(1)}% used</p>
              </div>
            </div>
          )
        })}
        {budgets.length === 0 && <p className="text-gray-500">No budgets yet.</p>}
      </div>
    </div>
  )
}
