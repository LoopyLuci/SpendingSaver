import { useEffect, useState } from 'react'
import { db, Transaction } from '../db/db'
import { getNetWorth, getSpendingByCategory } from '../services/budget'
import { ResponsiveContainer, PieChart, Pie, Cell, Tooltip, Legend } from 'recharts'

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#AF19FF', '#FF4560', '#00E396']

export default function Dashboard() {
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [netWorth, setNetWorth] = useState(0)
  const [categoryData, setCategoryData] = useState<Array<{ name: string; value: number }>>([])
  const [totalIncome, setTotalIncome] = useState(0)
  const [totalExpenses, setTotalExpenses] = useState(0)

  useEffect(() => {
    const load = async () => {
      const txs = await db.transactions.toArray()
      setTransactions(txs)
      setNetWorth(await getNetWorth())
      setCategoryData(await getSpendingByCategory())

      const income = txs.filter(t => t.amount > 0).reduce((s, t) => s + t.amount, 0)
      const expenses = txs.filter(t => t.amount < 0).reduce((s, t) => s + Math.abs(t.amount), 0)
      setTotalIncome(income)
      setTotalExpenses(expenses)
    }
    load()
  }, [])

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <p className="text-sm text-gray-500 dark:text-gray-400">Net Worth</p>
          <p className="text-3xl font-semibold">${netWorth.toFixed(2)}</p>
        </div>
        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <p className="text-sm text-gray-500 dark:text-gray-400">Total Income</p>
          <p className="text-3xl font-semibold text-green-600 dark:text-green-400">${totalIncome.toFixed(2)}</p>
        </div>
        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <p className="text-sm text-gray-500 dark:text-gray-400">Total Expenses</p>
          <p className="text-3xl font-semibold text-red-600 dark:text-red-400">${totalExpenses.toFixed(2)}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <h2 className="text-lg font-semibold mb-4">Spending by Category</h2>
          {categoryData.length === 0 ? (
            <p className="text-gray-500">No expense data yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={categoryData}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  outerRadius={80}
                  fill="#8884d8"
                  label
                >
                  {categoryData.map((entry, index) => (
                    <Cell key={`cell-${entry.name}-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <h2 className="text-lg font-semibold mb-4">Recent Transactions</h2>
          {transactions.length === 0 ? (
            <p className="text-gray-500">No transactions yet.</p>
          ) : (
            <ul className="divide-y divide-gray-200 dark:divide-gray-700">
              {transactions.slice(0, 10).map(tx => (
                <li key={tx.id} className="py-2 flex justify-between">
                  <span className="truncate mr-4">{tx.description}</span>
                  <span className={tx.amount < 0 ? 'text-red-600' : 'text-green-600'}>
                    {tx.amount < 0 ? '-' : ''}${Math.abs(tx.amount).toFixed(2)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}
