import { useState, useEffect } from 'react'
import { db, Account } from '../db/db'

export default function Accounts() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [form, setForm] = useState({ name: '', type: 'checking' as Account['type'], balance: 0, currency: 'USD' })

  const load = async () => setAccounts(await db.accounts.toArray())
  useEffect(() => { load() }, [])

  const handleAdd = async () => {
    if (!form.name) return
    await db.accounts.add({
      name: form.name,
      type: form.type,
      balance: form.balance,
      currency: form.currency,
      createdAt: new Date(),
    })
    setForm({ name: '', type: 'checking', balance: 0, currency: 'USD' })
    load()
  }

  const handleDelete = async (id: number) => {
    await db.accounts.delete(id)
    load()
  }

  const totalBalance = accounts.reduce((s, a) => s + a.balance, 0)

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">Accounts</h1>
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-4 mb-6">
        <h2 className="font-semibold mb-2">Add Account</h2>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
          <input
            type="text"
            placeholder="Account name"
            className="border rounded p-2 dark:bg-gray-700"
            value={form.name}
            onChange={e => setForm({ ...form, name: e.target.value })}
          />
          <select
            className="border rounded p-2 dark:bg-gray-700"
            value={form.type}
            onChange={e => setForm({ ...form, type: e.target.value as Account['type'] })}
          >
            <option value="checking">Checking</option>
            <option value="savings">Savings</option>
            <option value="credit">Credit</option>
            <option value="investment">Investment</option>
            <option value="other">Other</option>
          </select>
          <input
            type="number"
            step="0.01"
            placeholder="Balance"
            className="border rounded p-2 dark:bg-gray-700"
            value={form.balance || ''}
            onChange={e => setForm({ ...form, balance: parseFloat(e.target.value) || 0 })}
          />
          <input
            type="text"
            placeholder="Currency"
            className="border rounded p-2 dark:bg-gray-700"
            value={form.currency}
            onChange={e => setForm({ ...form, currency: e.target.value })}
          />
          <button
            onClick={handleAdd}
            className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700"
          >
            Add Account
          </button>
        </div>
      </div>

      <div className="overflow-x-auto bg-white dark:bg-gray-800 rounded-lg shadow">
        <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          <thead className="bg-gray-50 dark:bg-gray-700">
            <tr>
              <th className="px-4 py-3 text-left">Name</th>
              <th className="px-4 py-3 text-left">Type</th>
              <th className="px-4 py-3 text-right">Balance</th>
              <th className="px-4 py-3 text-left">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
            {accounts.map(acc => (
              <tr key={acc.id}>
                <td className="px-4 py-2">{acc.name}</td>
                <td className="px-4 py-2 capitalize">{acc.type}</td>
                <td className="px-4 py-2 text-right">${acc.balance.toFixed(2)}</td>
                <td className="px-4 py-2">
                  <button onClick={() => handleDelete(acc.id!)} className="text-red-600 hover:underline">Delete</button>
                </td>
              </tr>
            ))}
            {accounts.length === 0 && (
              <tr><td colSpan={4} className="text-center py-4 text-gray-500">No accounts</td></tr>
            )}
          </tbody>
          <tfoot className="bg-gray-50 dark:bg-gray-700">
            <tr>
              <td colSpan={2} className="px-4 py-2 font-semibold">Total</td>
              <td className="px-4 py-2 text-right font-semibold">${totalBalance.toFixed(2)}</td>
              <td></td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  )
}
