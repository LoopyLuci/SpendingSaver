import { useEffect, useState } from 'react'
import { listInvestments, createInvestment, getInvestmentPerformance } from '../hooks/useBackend'
import { ResponsiveContainer, PieChart, Pie, Cell, Tooltip, Legend } from 'recharts'

const COLORS = ['#3b82f6', '#22c55e', '#f97316', '#ef4444', '#8b5cf6', '#06b6d4']

export default function Investments() {
  const [holdings, setHoldings] = useState<any[]>([])
  const [performance, setPerformance] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({ name: '', ticker: '', shares: '', cost_basis: '', current_price: '', investment_type: 'stock' })

  useEffect(() => { load() }, [])

  const load = async () => {
    setLoading(true)
    try {
      const [inv, perf] = await Promise.all([listInvestments(), getInvestmentPerformance()])
      setHoldings(inv.holdings || [])
      setPerformance(perf)
    } catch (e: any) { setError(e.message) } finally { setLoading(false) }
  }

  const add = async () => {
    setError(null)
    try {
      await createInvestment({ ...form, shares: parseFloat(form.shares || '0'), cost_basis: parseFloat(form.cost_basis || '0'), current_price: parseFloat(form.current_price || '0') })
      setForm({ name: '', ticker: '', shares: '', cost_basis: '', current_price: '', investment_type: 'stock' })
      load()
    } catch (e: any) { setError(e.message) }
  }

  const pieData = (performance?.by_type || []).map((t: any) => ({ name: t.type, value: t.value }))

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Portfolio Overview</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
          <div className="p-3 bg-blue-50 dark:bg-blue-900/20 rounded">
            <div className="text-sm text-gray-500">Total Value</div>
            <div className="text-xl font-bold text-blue-600">${(performance?.total_value || 0).toFixed(2)}</div>
          </div>
          <div className="p-3 bg-gray-50 dark:bg-gray-700 rounded">
            <div className="text-sm text-gray-500">Total Cost</div>
            <div className="text-xl font-bold">${(performance?.total_cost || 0).toFixed(2)}</div>
          </div>
          <div className={`p-3 rounded ${(performance?.total_gain_loss || 0) >= 0 ? 'bg-green-50 dark:bg-green-900/20' : 'bg-red-50 dark:bg-red-900/20'}`}>
            <div className="text-sm text-gray-500">Gain/Loss</div>
            <div className={`text-xl font-bold ${(performance?.total_gain_loss || 0) >= 0 ? 'text-green-600' : 'text-red-600'}`}>
              ${(performance?.total_gain_loss || 0).toFixed(2)}
            </div>
          </div>
          <div className="p-3 bg-purple-50 dark:bg-purple-900/20 rounded">
            <div className="text-sm text-gray-500">Holdings</div>
            <div className="text-xl font-bold text-purple-600">{holdings.length}</div>
          </div>
        </div>
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Allocation by Type</h3>
        {pieData.length > 0 ? (
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie data={pieData} cx="50%" cy="50%" outerRadius={100} dataKey="value" label={({ name, percent }: any) => `${name} ${(percent * 100).toFixed(0)}%`}>
                {pieData.map((_: any, i: number) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Pie>
              <Tooltip formatter={(v: any) => `$${Number(v).toFixed(2)}`} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        ) : <p className="text-gray-500">No data</p>}
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Add Holding</h3>
        {error && <div className="text-red-600 text-sm mb-2">{error}</div>}
        <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
          <input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="Name" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.ticker} onChange={e => setForm({ ...form, ticker: e.target.value })} placeholder="Ticker" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.shares} onChange={e => setForm({ ...form, shares: e.target.value })} placeholder="Shares" type="number" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.cost_basis} onChange={e => setForm({ ...form, cost_basis: e.target.value })} placeholder="Cost Basis" type="number" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.current_price} onChange={e => setForm({ ...form, current_price: e.target.value })} placeholder="Current Price" type="number" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <select value={form.investment_type} onChange={e => setForm({ ...form, investment_type: e.target.value })} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600">
            <option value="stock">Stock</option>
            <option value="bond">Bond</option>
            <option value="etf">ETF</option>
            <option value="crypto">Crypto</option>
            <option value="mutual_fund">Mutual Fund</option>
            <option value="other">Other</option>
          </select>
        </div>
        <button onClick={add} className="mt-3 px-4 py-2 bg-blue-600 text-white rounded">Add Holding</button>
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Holdings</h3>
        {loading && <p className="text-gray-500">Loading...</p>}
        {!loading && holdings.length === 0 && <p className="text-gray-500">No holdings yet.</p>}
        <ul className="divide-y dark:divide-gray-700">
          {holdings.map(h => (
            <li key={h.id} className="py-3 flex items-center justify-between">
              <div>
                <div className="font-medium">{h.name} {h.ticker && <span className="text-gray-500">({h.ticker})</span>}</div>
                <div className="text-sm text-gray-500">{h.investment_type}</div>
              </div>
              <div className="text-right">
                <div className="font-bold">${h.value.toFixed(2)}</div>
                <div className={`text-sm ${h.gain_loss >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                  {h.gain_loss >= 0 ? '+' : ''}{h.gain_loss.toFixed(2)}
                </div>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
