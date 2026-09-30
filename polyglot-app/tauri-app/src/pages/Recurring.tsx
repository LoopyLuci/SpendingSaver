import { useEffect, useState } from 'react'
import { listRecurringTransactions, createRecurringTransaction, updateRecurringTransaction, deleteRecurringTransaction, listCategories, runRecurringScheduler, getSchedulerStatus, frontendLog } from '../hooks/useBackend'

export default function Recurring() {
  const [rows, setRows] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [categories, setCategories] = useState<Array<{ id: string; name: string }>>([])
  const [form, setForm] = useState({ account_id: '', amount: '', description: '', frequency: 'monthly', next_date: '', category_id: '' })
  const [editId, setEditId] = useState<string | null>(null)
  const [scheduler, setScheduler] = useState<{ last_run_secs: number | null; next_run_secs: number | null; running: boolean }>({ last_run_secs: null, next_run_secs: null, running: false })

  useEffect(() => {
    listCategories().then(setCategories).catch(() => {})
  }, [])

  const load = async () => {
    setLoading(true)
    setError(null)
    try { setRows(await listRecurringTransactions()) } catch (e: any) { setError(e.message) } finally { setLoading(false) }
  }

  useEffect(() => {
    load()
  }, [])

  useEffect(() => {
    const tick = setInterval(async () => {
      try {
        const s = await getSchedulerStatus()
        setScheduler({ last_run_secs: (s as any).last_run_secs, next_run_secs: (s as any).next_run_secs, running: (s as any).running })
      } catch {}
    }, 1000)
    return () => clearInterval(tick)
  }, [])

  const save = async () => {
    setError(null)
    try {
      if (editId) {
        await updateRecurringTransaction(editId, { ...form, amount: parseFloat(form.amount || '0'), active: true })
        setEditId(null)
      } else {
        await createRecurringTransaction({ ...form, amount: parseFloat(form.amount || '0') })
      }
      setForm({ account_id: '', amount: '', description: '', frequency: 'monthly', next_date: '', category_id: '' })
      load()
    } catch (e: any) { setError(e.message) }
  }

  const startEdit = (row: any) => {
    setEditId(row.id)
    setForm({ account_id: row.account_id, amount: String(row.amount ?? ''), description: row.description ?? '', frequency: row.frequency ?? 'monthly', next_date: row.next_date ?? '', category_id: row.category_id ?? '' })
  }

  const remove = async (id: string) => {
    if (!confirm('Delete this recurring transaction?')) return
    setError(null)
    try { await deleteRecurringTransaction(id); load() } catch (e: any) { setError(e.message) }
  }

  const runNow = async () => {
    try {
      await runRecurringScheduler()
      await frontendLog('recurring scheduler manual run')
      load()
    } catch (e: any) { setError(e.message) }
  }

  const categoryName = (id: string) => categories.find(c => c.id === id)?.name || 'Uncategorized'

  const formatSecs = (secs: number | null) => {
    if (secs === null) return '--'
    const h = Math.floor(secs / 3600)
    const m = Math.floor((secs % 3600) / 60)
    return `${h}h ${m}m`
  }

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold">Scheduler</h3>
          <button onClick={runNow} disabled={scheduler.running} className="px-3 py-1 border rounded disabled:opacity-50">
            {scheduler.running ? 'Running...' : 'Run now'}
          </button>
        </div>
        <div className="text-sm text-gray-600 dark:text-gray-300">
          <div>Auto-runs every 6 hours</div>
          <div>Last run: {scheduler.last_run_secs === null ? 'never' : `${formatSecs(scheduler.last_run_secs)} ago`}</div>
          <div>Next run in: {formatSecs(scheduler.next_run_secs)}</div>
          <div className="text-xs text-gray-500">Auto-runs every 6 hours</div>
        </div>
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">{editId ? 'Edit' : 'New'} Recurring Transaction</h3>
        {error && <div className="text-red-600 text-sm mb-2">{error}</div>}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <input className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" placeholder="Account ID" value={form.account_id} onChange={e => setForm({ ...form, account_id: e.target.value })} />
          <input className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" placeholder="Amount" type="number" value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} />
          <input className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" placeholder="Description" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} />
          <select className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" value={form.frequency} onChange={e => setForm({ ...form, frequency: e.target.value })}>
            <option value="daily">Daily</option>
            <option value="weekly">Weekly</option>
            <option value="monthly">Monthly</option>
            <option value="annual">Annual</option>
          </select>
          <input className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" placeholder="Next date (YYYY-MM-DD)" value={form.next_date} onChange={e => setForm({ ...form, next_date: e.target.value })} />
          <select className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" value={form.category_id} onChange={e => setForm({ ...form, category_id: e.target.value })}>
            <option value="">Uncategorized</option>
            {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <div className="mt-3 flex gap-2">
          <button onClick={save} className="px-4 py-2 bg-blue-600 text-white rounded">{editId ? 'Update' : 'Create'}</button>
          {editId && <button onClick={() => { setEditId(null); setForm({ account_id: '', amount: '', description: '', frequency: 'monthly', next_date: '', category_id: '' }) }} className="px-4 py-2 border rounded">Cancel</button>}
        </div>
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold">Recurring Transactions</h3>
        </div>
        {loading && <p className="text-gray-500">Loading...</p>}
        {error && <div className="text-red-600 text-sm">{error}</div>}
        {!loading && rows.length === 0 && <p className="text-gray-500">None yet.</p>}
        <ul className="divide-y dark:divide-gray-700">
          {rows.map(row => (
            <li key={row.id} className="py-3 flex items-center justify-between">
              <div>
                <div className="font-medium">{row.description || 'Untitled'}</div>
                <div className="text-sm text-gray-500">{row.frequency} • next {row.next_date} • acct {row.account_id} • {categoryName(row.category_id || '')}</div>
              </div>
              <div className="flex gap-2">
                <button onClick={() => startEdit(row)} className="px-3 py-1 border rounded">Edit</button>
                <button onClick={() => remove(row.id)} className="px-3 py-1 border rounded text-red-600">Delete</button>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
