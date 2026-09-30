import { useEffect, useState } from 'react'
import { getSpendingInsights, getNetIncomeReport, getBudgetComparison } from '../hooks/useBackend'
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts'

export default function Insights() {
  const [insights, setInsights] = useState<any>(null)
  const [netIncome, setNetIncome] = useState<any>(null)
  const [budgetComp, setBudgetComp] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => { load() }, [])

  const load = async () => {
    setLoading(true)
    try {
      const [ins, ni, bc] = await Promise.all([getSpendingInsights(), getNetIncomeReport(12), getBudgetComparison()])
      setInsights(ins)
      setNetIncome(ni)
      setBudgetComp(bc)
    } catch (e: any) { setError(e.message) } finally { setLoading(false) }
  }

  const incomeChartData = (netIncome?.months || []).map((m: any) => ({ month: m.month, income: m.income, expenses: m.expenses, net: m.net }))

  return (
    <div className="space-y-6">
      {loading && <div className="text-gray-500 text-center py-8">Loading insights...</div>}
      {error && <div className="text-red-600 text-center py-8">{error}</div>}

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Spending Trend</h3>
        <div className="mb-3">
          <span className={`inline-block px-3 py-1 rounded-full text-sm font-medium ${
            insights?.trend === 'increasing' ? 'bg-red-100 text-red-700' :
            insights?.trend === 'decreasing' ? 'bg-green-100 text-green-700' :
            'bg-gray-100 text-gray-700'
          }`}>
            {insights?.trend === 'increasing' ? '↑ Increasing' : insights?.trend === 'decreasing' ? '↓ Decreasing' : '→ Stable'}
          </span>
          <span className="ml-3 text-gray-500">Avg: ${(insights?.average || 0).toFixed(2)}/mo</span>
        </div>
        {insights?.recommendations?.length > 0 && (
          <ul className="space-y-1 text-sm text-gray-600">
            {insights.recommendations.map((r: string, i: number) => <li key={i}>• {r}</li>)}
          </ul>
        )}
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Income vs Expenses</h3>
        {incomeChartData.length > 0 ? (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={incomeChartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="month" />
              <YAxis />
              <Tooltip formatter={(v: any) => `$${Number(v).toFixed(2)}`} />
              <Legend />
              <Bar dataKey="income" fill="#22c55e" name="Income" />
              <Bar dataKey="expenses" fill="#ef4444" name="Expenses" />
            </BarChart>
          </ResponsiveContainer>
        ) : <p className="text-gray-500">No data</p>}
        {netIncome?.averages && (
          <div className="mt-3 text-sm text-gray-500">
            Avg savings rate: {netIncome.averages.savings_rate}%
          </div>
        )}
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Budget vs Actual</h3>
        {budgetComp.length > 0 ? (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={budgetComp}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="category_name" />
              <YAxis />
              <Tooltip formatter={(v: any) => `$${Number(v).toFixed(2)}`} />
              <Legend />
              <Bar dataKey="budget" fill="#3b82f6" name="Budget" />
              <Bar dataKey="spent" fill="#f97316" name="Spent" />
            </BarChart>
          </ResponsiveContainer>
        ) : <p className="text-gray-500">No budgets set</p>}
      </section>

      {insights?.anomalies?.length > 0 && (
        <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
          <h3 className="text-lg font-semibold mb-4">Anomalies Detected</h3>
          <ul className="space-y-2">
            {insights.anomalies.map((a: any, i: number) => (
              <li key={i} className="flex justify-between p-2 bg-yellow-50 dark:bg-yellow-900/20 rounded">
                <span>{a.month}</span>
                <span className="font-bold">${a.amount.toFixed(2)} — {a.reason}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
