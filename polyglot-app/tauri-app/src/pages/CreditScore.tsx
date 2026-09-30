import { useEffect, useState } from 'react'
import { listCreditScores, addCreditScore } from '../hooks/useBackend'
import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip } from 'recharts'

export default function CreditScore() {
  const [scores, setScores] = useState<any[]>([])
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({ score: '', provider: '', factors: '' })

  useEffect(() => { load() }, [])

  const load = async () => {
    try { setScores(await listCreditScores()) } catch (e: any) { setError(e.message) }
  }

  const add = async () => {
    setError(null)
    try {
      await addCreditScore(parseInt(form.score || '0'), form.provider, form.factors)
      setForm({ score: '', provider: '', factors: '' })
      load()
    } catch (e: any) { setError(e.message) }
  }

  const latest = scores[0]
  const chartData = scores.slice().reverse().map(s => ({ date: s.recorded_at?.slice(0, 10) || '', score: s.score }))

  const getScoreColor = (score: number) => {
    if (score >= 750) return 'text-green-600'
    if (score >= 700) return 'text-blue-600'
    if (score >= 650) return 'text-yellow-600'
    return 'text-red-600'
  }

  const getScoreLabel = (score: number) => {
    if (score >= 750) return 'Excellent'
    if (score >= 700) return 'Good'
    if (score >= 650) return 'Fair'
    return 'Poor'
  }

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Credit Score</h3>
        {latest ? (
          <div className="text-center mb-4">
            <div className={`text-6xl font-bold ${getScoreColor(latest.score)}`}>{latest.score}</div>
            <div className="text-lg text-gray-500">{getScoreLabel(latest.score)}</div>
            <div className="text-sm text-gray-400">{latest.provider} • {latest.recorded_at?.slice(0, 10)}</div>
            {latest.factors && <div className="mt-2 text-sm text-gray-500">{latest.factors}</div>}
          </div>
        ) : <p className="text-gray-500 text-center">No scores recorded yet</p>}
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Score History</h3>
        {chartData.length > 1 ? (
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" />
              <YAxis domain={[300, 850]} />
              <Tooltip />
              <Line type="monotone" dataKey="score" stroke="#3b82f6" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        ) : <p className="text-gray-500">Need at least 2 data points for a chart</p>}
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Record Score</h3>
        {error && <div className="text-red-600 text-sm mb-2">{error}</div>}
        <div className="grid grid-cols-3 gap-3">
          <input value={form.score} onChange={e => setForm({ ...form, score: e.target.value })} placeholder="Score (300-850)" type="number" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.provider} onChange={e => setForm({ ...form, provider: e.target.value })} placeholder="Provider" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.factors} onChange={e => setForm({ ...form, factors: e.target.value })} placeholder="Factors" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
        </div>
        <button onClick={add} className="mt-3 px-4 py-2 bg-blue-600 text-white rounded">Record Score</button>
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">History</h3>
        <ul className="divide-y dark:divide-gray-700">
          {scores.map(s => (
            <li key={s.id} className="py-2 flex justify-between">
              <span className={getScoreColor(s.score)}>{s.score}</span>
              <span className="text-gray-500">{s.provider} • {s.recorded_at?.slice(0, 10)}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
