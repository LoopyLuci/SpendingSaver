import { useEffect, useState } from 'react'
import { listAccounts, createAccount, updateAccount, deleteAccount } from '../hooks/useBackend'

export default function Accounts() {
  const [accounts, setAccounts] = useState<any[]>([])
  const [name, setName] = useState('')
  const [type, setType] = useState('checking')
  const [balance, setBalance] = useState('0')
  const [currency, setCurrency] = useState('USD')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [editType, setEditType] = useState('checking')
  const [editBalance, setEditBalance] = useState('0')
  const [editCurrency, setEditCurrency] = useState('USD')

  useEffect(() => {
    listAccounts()
      .then(setAccounts)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await createAccount({ name, type, balance: parseFloat(balance), currency })
      setName('')
      setBalance('0')
      const updated = await listAccounts()
      setAccounts(updated)
    } catch (e: any) {
      setError(e.message)
    }
  }

  function startEdit(acc: any) {
    setEditingId(acc.id)
    setEditName(acc.name)
    setEditType(acc.type)
    setEditBalance(String(acc.balance))
    setEditCurrency(acc.currency)
  }

  async function saveEdit(id: string) {
    try {
      await updateAccount(id, { name: editName, type: editType, balance: parseFloat(editBalance), currency: editCurrency })
      setEditingId(null)
      const updated = await listAccounts()
      setAccounts(updated)
    } catch (e: any) {
      setError(e.message)
    }
  }

  async function remove(id: string) {
    if (!confirm('Delete this account?')) return
    try {
      await deleteAccount(id)
      setAccounts(prev => prev.filter(a => a.id !== id))
    } catch (e: any) {
      setError(e.message)
    }
  }

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Add Account</h3>
        {error && <div className="text-red-600 text-sm mb-3">{error}</div>}
        <form className="space-y-3" onSubmit={submit}>
          <div className="grid gap-3 md:grid-cols-2">
            <input value={name} onChange={e => setName(e.target.value)} placeholder="Name" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
            <select value={type} onChange={e => setType(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600">
              <option value="checking">Checking</option>
              <option value="savings">Savings</option>
              <option value="credit">Credit</option>
              <option value="investment">Investment</option>
              <option value="other">Other</option>
            </select>
            <input type="number" value={balance} onChange={e => setBalance(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
            <input value={currency} onChange={e => setCurrency(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          </div>
          <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded">Save</button>
        </form>
      </section>
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Accounts</h3>
        {loading ? (
          <div className="text-sm text-gray-500">Loading...</div>
        ) : (
          <ul className="space-y-2">
            {accounts.map(acc => (
              <li key={acc.id} className="flex items-center justify-between gap-3">
                {editingId === acc.id ? (
                  <div className="flex flex-1 gap-2">
                    <input value={editName} onChange={e => setEditName(e.target.value)} className="border rounded p-1 dark:bg-gray-700 dark:border-gray-600" />
                    <select value={editType} onChange={e => setEditType(e.target.value)} className="border rounded p-1 dark:bg-gray-700 dark:border-gray-600">
                      <option value="checking">Checking</option>
                      <option value="savings">Savings</option>
                      <option value="credit">Credit</option>
                      <option value="investment">Investment</option>
                      <option value="other">Other</option>
                    </select>
                    <input type="number" value={editBalance} onChange={e => setEditBalance(e.target.value)} className="border rounded p-1 dark:bg-gray-700 dark:border-gray-600" />
                    <input value={editCurrency} onChange={e => setEditCurrency(e.target.value)} className="border rounded p-1 dark:bg-gray-700 dark:border-gray-600" />
                    <button onClick={() => saveEdit(acc.id)} className="px-2 py-1 bg-green-600 text-white rounded">Save</button>
                    <button onClick={() => setEditingId(null)} className="px-2 py-1 border rounded">Cancel</button>
                  </div>
                ) : (
                  <>
                    <div>
                      <div>{acc.name}</div>
                      <div className="text-xs text-gray-500">{acc.type} • {acc.currency}</div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="font-medium">${Number(acc.balance).toFixed(2)}</span>
                      <button onClick={() => startEdit(acc)} className="px-2 py-1 border rounded">Edit</button>
                      <button onClick={() => remove(acc.id)} className="px-2 py-1 border rounded text-red-600">Delete</button>
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
