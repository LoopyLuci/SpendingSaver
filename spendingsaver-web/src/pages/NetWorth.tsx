import { useEffect, useState } from 'react'
import { db, Account } from '../db/db'
import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts'

export default function NetWorth() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [netWorthHistory, setNetWorthHistory] = useState<Array<{ date: string; netWorth: number }>>([])

  useEffect(() => {
    const load = async () => {
      const accs = await db.accounts.toArray()
      setAccounts(accs)

      const txs = await db.transactions.orderBy('date').toArray()
      const balanceMap = new Map<string, number>()
      let running = 0
      for (const tx of txs) {
        running += tx.amount
        balanceMap.set(tx.date, running)
      }
      const history = Array.from(balanceMap.entries()).map(([date, net]) => ({ date, netWorth: net }))
      setNetWorthHistory(history)
    }
    load()
  }, [])

  const totalAssets = accounts.filter(a => a.balance >= 0).reduce((s, a) => s + a.balance, 0)
  const totalLiabilities = accounts.filter(a => a.balance < 0).reduce((s, a) => s + Math.abs(a.balance), 0)

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">Net Worth</h1>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <p className="text-sm text-gray-500">Assets</p>
          <p className="text-2xl font-semibold text-green-600">${totalAssets.toFixed(2)}</p>
        </div>
        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <p className="text-sm text-gray-500">Liabilities</p>
          <p className="text-2xl font-semibold text-red-600">${totalLiabilities.toFixed(2)}</p>
        </div>
        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <p className="text-sm text-gray-500">Net Worth</p>
          <p className="text-2xl font-semibold">${(totalAssets - totalLiabilities).toFixed(2)}</p>
        </div>
      </div>

      {netWorthHistory.length > 0 && (
        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <h2 className="text-lg font-semibold mb-4">Net Worth Over Time</h2>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={netWorthHistory}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="netWorth" stroke="#8884d8" />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}
