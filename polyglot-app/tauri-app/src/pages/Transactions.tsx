import { useEffect, useState } from 'react'
import { listTransactions, createTransaction, updateTransaction, deleteTransaction, suggestTransactionCategories, listCategories, splitTransaction } from '../hooks/useBackend'

export default function Transactions() {
  const [transactions, setTransactions] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [accountId, setAccountId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [categories, setCategories] = useState<any[]>([])
  const [accounts, setAccounts] = useState<any[]>([])
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editDescription, setEditDescription] = useState('')
  const [editAmount, setEditAmount] = useState('')
  const [suggestions, setSuggestions] = useState<Array<{ category_id: string; description: string }>>([])
  const [splittingId, setSplittingId] = useState<string | null>(null)
  const [splits, setSplits] = useState<Array<{ category_id: string; amount: string }>>([])
  const [splitError, setSplitError] = useState<string | null>(null)

  useEffect(() => { load() }, [])

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const tx = await listTransactions()
      setTransactions(tx)
      const cats = await listCategories()
      setCategories(cats)
      const accs = await (await fetch('http://127.0.0.1:8125/api/accounts')).json()
      setAccounts(accs)
    } catch (e: any) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  async function handleDescriptionChange(value: string) {
    setDescription(value)
    await new Promise(resolve => setTimeout(resolve, 250))
    const trimmed = value.trim()
    if (trimmed.length >= 2) {
      const sugg = await suggestTransactionCategories(trimmed)
      setSuggestions(sugg)
    } else {
      setSuggestions([])
    }
  }

  function applySuggestion(category_id: string) {
    setCategoryId(category_id)
    setSuggestions([])
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!accountId || !description || !amount) return
    setError(null)
    try {
      await createTransaction({
        account_id: accountId,
        amount: parseFloat(amount),
        description,
        category_id: categoryId || undefined,
        date,
      })
      setDescription('')
      setAmount('')
      setCategoryId('')
      setSuggestions([])
      load()
    } catch (e: any) {
      setError(e.message)
    }
  }

  function startEdit(tx: any) {
    setEditingId(tx.id)
    setEditDescription(tx.description)
    setEditAmount(String(tx.amount))
  }

  async function saveEdit(id: string) {
    try {
      await updateTransaction(id, { description: editDescription, amount: parseFloat(editAmount) })
      setEditingId(null)
      load()
    } catch (e: any) {
      setError(e.message)
    }
  }

  async function remove(id: string) {
    try {
      await deleteTransaction(id)
      setTransactions(prev => prev.filter(t => t.id !== id))
    } catch (e: any) {
      setError(e.message)
    }
  }

  function startSplit(tx: any) {
    // Begin with 2 empty splits; the leftover is shown as we type.
    setSplitError(null)
    setSplittingId(tx.id)
    setSplits([
      { category_id: '', amount: '' },
      { category_id: '', amount: '' },
    ])
  }

  function cancelSplit() {
    setSplittingId(null)
    setSplits([])
    setSplitError(null)
  }

  function updateSplit(index: number, field: 'category_id' | 'amount', value: string) {
    setSplits(prev => prev.map((s, i) => (i === index ? { ...s, [field]: value } : s)))
  }

  function splitSum(): number {
    return splits.reduce((acc, s) => acc + (parseFloat(s.amount) || 0), 0)
  }

  function addSplitRow() {
    setSplits(prev => [...prev, { category_id: '', amount: '' }])
  }

  function removeSplitRow(index: number) {
    if (splits.length <= 2) return
    setSplits(prev => prev.filter((_, i) => i !== index))
  }

  async function submitSplit(tx: any) {
    const total = parseFloat(tx.amount)
    const sum = splitSum()
    if (splits.length < 2) {
      setSplitError('Add at least 2 splits.')
      return
    }
    if (splits.some(s => !s.category_id || s.amount === '')) {
      setSplitError('Every split needs a category and an amount.')
      return
    }
    if (Math.abs(sum - total) > 0.001) {
      setSplitError(`Split total (${sum.toFixed(2)}) must equal the transaction amount (${total.toFixed(2)}). Leftover: ${(total - sum).toFixed(2)}`)
      return
    }
    setSplitError(null)
    try {
      await splitTransaction(tx.id, splits.map(s => ({ category_id: s.category_id, amount: parseFloat(s.amount) })))
      cancelSplit()
      load()
    } catch (e: any) {
      setSplitError(e.message)
    }
  }

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Add Transaction</h3>
        {error && <div className="text-red-600 text-sm mb-3">{error}</div>}
        <form className="space-y-3" onSubmit={submit}>
          <div className="grid gap-3 md:grid-cols-2">
            <select value={accountId} onChange={e => setAccountId(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600">
              <option value="">Select account</option>
              {accounts.map(a => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>
            <select value={categoryId} onChange={e => setCategoryId(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600">
              <option value="">No category</option>
              {categories.map(c => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
            <div className="relative">
              <input value={description} onChange={e => handleDescriptionChange(e.target.value)} placeholder="Description" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600 w-full" />
              {suggestions.length > 0 && (
                <div className="absolute z-10 mt-1 w-full bg-white dark:bg-gray-700 border dark:border-gray-600 rounded shadow">
                  {suggestions.map(s => (
                    <button type="button" key={s.category_id} onClick={() => applySuggestion(s.category_id)} className="block w-full text-left px-2 py-1 hover:bg-gray-100 dark:hover:bg-gray-600 text-sm">
                      {s.description}
                    </button>
                  ))}
                </div>
              )}
            </div>
            <input type="number" value={amount} onChange={e => setAmount(e.target.value)} placeholder="Amount" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
            <input type="date" value={date} onChange={e => setDate(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          </div>
          <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded">Save</button>
        </form>
      </section>
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Transactions</h3>
        {loading ? (
          <div className="text-sm text-gray-500">Loading...</div>
        ) : (
          <ul className="space-y-2">
            {transactions.map(tx => (
              <li key={tx.id} className={splittingId === tx.id ? 'space-y-3 bg-gray-50 dark:bg-gray-700/40 rounded p-3' : 'flex items-center justify-between gap-3'}>
                {splittingId === tx.id ? (
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="font-medium">{tx.description}</div>
                        <div className="text-xs text-gray-500">{tx.date} · ${tx.amount.toFixed(2)}</div>
                      </div>
                      <button onClick={cancelSplit} className="px-2 py-1 border rounded text-sm">Cancel</button>
                    </div>
                    {splitError && <div className="text-red-600 text-sm">{splitError}</div>}
                    <div className="grid gap-2 md:grid-cols-[1fr_120px_auto] items-center">
                      {splits.map((s, i) => (
                        <div key={i} className="contents md:contents">
                          <select
                            value={s.category_id}
                            onChange={e => updateSplit(i, 'category_id', e.target.value)}
                            className="border rounded p-1 dark:bg-gray-700 dark:border-gray-600"
                          >
                            <option value="">Select category</option>
                            {categories.map(c => (
                              <option key={c.id} value={c.id}>{c.name}</option>
                            ))}
                          </select>
                          <input
                            type="number"
                            step="any"
                            value={s.amount}
                            onChange={e => updateSplit(i, 'amount', e.target.value)}
                            placeholder="Amount"
                            className="border rounded p-1 dark:bg-gray-700 dark:border-gray-600"
                          />
                          <button onClick={() => removeSplitRow(i)} className="px-2 py-1 border rounded text-red-600 text-sm" title="Remove split">×</button>
                        </div>
                      ))}
                    </div>
                    <div className="flex flex-wrap items-center gap-3">
                      <button onClick={addSplitRow} className="px-2 py-1 border rounded text-sm">Add split</button>
                      <span className="text-sm text-gray-500">
                        Total: ${splitSum().toFixed(2)}
                      </span>
                      <span className={`text-sm ${splitSum() > tx.amount + 0.001 ? 'text-red-600' : 'text-gray-500'}`}>
                        Leftover: ${(tx.amount - splitSum()).toFixed(2)}
                      </span>
                      <button onClick={() => submitSplit(tx)} className="px-3 py-1 bg-blue-600 text-white rounded text-sm ml-auto">Submit Split</button>
                    </div>
                  </div>
                ) : editingId === tx.id ? (
                  <div className="flex flex-1 gap-2">
                    <input value={editDescription} onChange={e => setEditDescription(e.target.value)} className="border rounded p-1 dark:bg-gray-700 dark:border-gray-600" />
                    <input type="number" value={editAmount} onChange={e => setEditAmount(e.target.value)} className="border rounded p-1 dark:bg-gray-700 dark:border-gray-600" />
                    <button onClick={() => saveEdit(tx.id)} className="px-2 py-1 bg-green-600 text-white rounded">Save</button>
                    <button onClick={() => setEditingId(null)} className="px-2 py-1 border rounded">Cancel</button>
                  </div>
                ) : (
                  <>
                    <div>
                      <div>{tx.description}</div>
                      <div className="text-xs text-gray-500">{tx.date}</div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="font-medium">${tx.amount.toFixed(2)}</span>
                      <button onClick={() => startEdit(tx)} className="px-2 py-1 border rounded">Edit</button>
                      <button onClick={() => startSplit(tx)} className="px-2 py-1 border rounded">Split</button>
                      <button onClick={() => remove(tx.id)} className="px-2 py-1 border rounded text-red-600">Delete</button>
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
