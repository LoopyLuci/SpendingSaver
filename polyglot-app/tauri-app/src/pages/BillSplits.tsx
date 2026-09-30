import { useEffect, useState } from 'react'
import { listBillSplits, createBillSplit } from '../hooks/useBackend'

export default function BillSplits() {
  const [splits, setSplits] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({ total_amount: '', description: '' })
  const [participants, setParticipants] = useState<Array<{ name: string; amount_owed: string }>>([{ name: '', amount_owed: '' }])

  useEffect(() => { load() }, [])

  const load = async () => {
    setLoading(true)
    try { setSplits(await listBillSplits()) } catch (e: any) { setError(e.message) } finally { setLoading(false) }
  }

  const addSplit = async () => {
    setError(null)
    try {
      await createBillSplit({
        total_amount: parseFloat(form.total_amount || '0'),
        description: form.description,
        participants: participants.filter(p => p.name).map(p => ({ name: p.name, amount_owed: parseFloat(p.amount_owed || '0'), paid: false })),
      })
      setForm({ total_amount: '', description: '' })
      setParticipants([{ name: '', amount_owed: '' }])
      load()
    } catch (e: any) { setError(e.message) }
  }

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">New Bill Split</h3>
        {error && <div className="text-red-600 text-sm mb-2">{error}</div>}
        <div className="grid grid-cols-2 gap-3 mb-3">
          <input value={form.total_amount} onChange={e => setForm({ ...form, total_amount: e.target.value })} placeholder="Total Amount" type="number" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} placeholder="Description" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
        </div>
        <div className="space-y-2 mb-3">
          {participants.map((p, i) => (
            <div key={i} className="flex gap-2">
              <input value={p.name} onChange={e => { const np = [...participants]; np[i].name = e.target.value; setParticipants(np) }} placeholder="Name" className="border rounded p-2 flex-1 dark:bg-gray-700 dark:border-gray-600" />
              <input value={p.amount_owed} onChange={e => { const np = [...participants]; np[i].amount_owed = e.target.value; setParticipants(np) }} placeholder="Amount" type="number" className="border rounded p-2 w-32 dark:bg-gray-700 dark:border-gray-600" />
              {participants.length > 1 && <button onClick={() => setParticipants(participants.filter((_, j) => j !== i))} className="px-2 text-red-600">×</button>}
            </div>
          ))}
        </div>
        <div className="flex gap-2">
          <button onClick={() => setParticipants([...participants, { name: '', amount_owed: '' }])} className="px-3 py-1 border rounded text-sm">+ Add Person</button>
          <button onClick={addSplit} className="px-4 py-2 bg-blue-600 text-white rounded">Create Split</button>
        </div>
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Bill Splits</h3>
        {loading && <p className="text-gray-500">Loading...</p>}
        {!loading && splits.length === 0 && <p className="text-gray-500">No splits yet.</p>}
        <ul className="divide-y dark:divide-gray-700">
          {splits.map(s => (
            <li key={s.id} className="py-3">
              <div className="flex justify-between mb-2">
                <div className="font-medium">{s.description || 'Untitled Split'}</div>
                <div className="font-bold">${s.total_amount.toFixed(2)}</div>
              </div>
              <div className="text-sm text-gray-500">
                {s.participants.map((p: any) => (
                  <span key={p.id} className={`mr-3 ${p.paid ? 'text-green-600' : 'text-red-600'}`}>
                    {p.name}: ${p.amount_owed.toFixed(2)} {p.paid ? '✓' : '○'}
                  </span>
                ))}
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
