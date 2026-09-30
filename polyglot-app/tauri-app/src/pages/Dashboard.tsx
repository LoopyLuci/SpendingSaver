import { useEffect, useState } from 'react'
import { getNetWorth, getNetWorthHistory, getSpendingByCategory, getBudgetAlerts } from '../hooks/useBackend'
import { useSettings } from '../hooks/useSettings'
import { ResponsiveContainer, PieChart, Pie, Cell, Tooltip, LineChart, Line, XAxis, YAxis, CartesianGrid } from 'recharts'

const COLORS = ['#3b82f6', '#f97316', '#8b5cf6', '#ec4899', '#06b6d4', '#ef4444', '#22c55e', '#64748b']

export default function Dashboard() {
  const { settings } = useSettings()
  const [netWorth, setNetWorth] = useState<number | null>(null)
  const [spending, setSpending] = useState<Array<{ name: string; value: number }>>([])
  const [history, setHistory] = useState<Array<{ date: string; net_worth: number }>>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [alerts, setAlerts] = useState<any[]>([])
  const [alertError, setAlertError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      getNetWorth().catch((e) => { setError(e.message); return null }),
      getSpendingByCategory().catch((e) => { setError(e.message); return [] }),
      getBudgetAlerts().catch((e) => { setAlertError(e.message); return [] }),
      getNetWorthHistory().catch((e) => { setError((prev) => prev ? prev + ' | ' + e.message : e.message); return [] }),
    ]).then(([net, cats, alertsData, hist]) => {
      if (net) setNetWorth(net.net_worth)
      if (cats) setSpending(cats)
      if (alertsData) setAlerts(alertsData)
      if (hist) setHistory(hist)
      setLoading(false)
    })
  }, [])

  return (
    <div className="space-y-6">
      {/* First-run onboarding — shows while there's no data yet. */}
      {!loading && netWorth !== null && netWorth === 0 && spending.length === 0 && (
        <section className="bg-gradient-to-r from-emerald-600 to-teal-600 dark:from-emerald-700 dark:to-teal-800 rounded-lg shadow p-6 text-white">
          <h2 className="text-2xl font-bold">Welcome to SpendingSaver 👋</h2>
          <p className="mt-2 text-emerald-50/90">
            Your private, offline-first finance tracker. Get started in three easy steps:
          </p>
          <ol className="mt-4 space-y-2 text-emerald-50/90 list-decimal list-inside">
            <li><strong className="text-white">Add an account</strong> — <em>Accounts</em> → give it a name and starting balance.</li>
            <li><strong className="text-white">Record your first transaction</strong> — <em>Transactions</em> → incoming income and daily spending.</li>
            <li><strong className="text-white">Set a budget</strong> — <em>Budgets</em> → keep spending in check per category.</li>
          </ol>
          <p className="mt-4 text-sm text-emerald-50/80">
            📱 Want it on your phone too? Open <em>Devices</em> in the sidebar to install and pair your Android via connection key.
          </p>
        </section>
      )}
      <section className="grid gap-4 md:grid-cols-2">
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
          <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400">Net Worth</h3>
          <div className="mt-2 text-3xl font-bold">
            {loading ? '...' : netWorth !== null ? (settings.showBalance ? `${settings.currency} ${netWorth.toFixed(2)}` : '••••') : '—'}
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
          <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400">Spending by Category</h3>
          <div className="mt-2 text-3xl font-bold">
            {loading ? '...' : settings.showBalance ? `${settings.currency} ${spending.reduce((s, c) => s + c.value, 0).toFixed(2)}` : '••••'}
          </div>
        </div>
      </section>
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Category Breakdown</h3>
        {error && <div className="text-red-600 text-sm mb-3">{error}</div>}
        {spending.length === 0 ? (
          <p className="text-gray-500">No spending data yet.</p>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie data={spending} dataKey="value" nameKey="name" innerRadius={60} outerRadius={100} paddingAngle={2}>
                  {spending.map((entry, index) => (
                    <Cell key={entry.name} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(value: any) => [`${settings.currency} ${Number(value).toFixed(2)}`, 'Spending']} />
              </PieChart>
            </ResponsiveContainer>
            <ul className="space-y-2">
              {spending.map((item, idx) => (
                <li key={item.name} className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="w-3 h-3 rounded" style={{ backgroundColor: COLORS[idx % COLORS.length] }} />
                    <span>{item.name}</span>
                  </div>
                  <span className="font-medium">{settings.currency} {item.value.toFixed(2)}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>
      {alertError && <div className="text-red-600 text-sm">{alertError}</div>}
      {alerts.length > 0 && (
        <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6 space-y-2">
          <h3 className="text-lg font-semibold mb-2">Budget Alerts</h3>
          {alerts.map((alert, idx) => (
            <div key={idx} className={`text-sm ${alert.status === 'over' ? 'text-red-600' : 'text-yellow-600'}`}>
              {alert.status === 'over'
                ? `Over budget: ${alert.category_id} spent ${settings.currency} ${alert.spent.toFixed(2)} of ${settings.currency} ${alert.budget.toFixed(2)}`
                : `Warning: ${alert.category_id} at ${(alert.percent * 100).toFixed(0)}% of budget`}
            </div>
          ))}
        </section>
      )}
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Net Worth Over Time</h3>
        {history.length === 0 ? (
          <p className="text-gray-500">No history yet. Add transactions to see trends.</p>
        ) : (
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={history}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" tick={{ fontSize: 12 }} />
              <YAxis tickFormatter={(v: any) => `${settings.currency} ${Number(v).toFixed(0)}`} tick={{ fontSize: 12 }} />
              <Tooltip formatter={(value: any) => [`${settings.currency} ${Number(value).toFixed(2)}`, 'Net Worth']} />
              <Line type="monotone" dataKey="net_worth" stroke="#3b82f6" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </section>
    </div>
  )
}
