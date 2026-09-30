import { useEffect, useState } from 'react'
import { getNetWorth, listAccounts } from '../hooks/useBackend'

export default function NetWorth() {
  const [netWorth, setNetWorth] = useState<number | null>(null)
  const [accounts, setAccounts] = useState<any[]>([])
  const [asOf, setAsOf] = useState<string>('')

  useEffect(() => {
    getNetWorth().then(d => {
      setNetWorth(d.net_worth)
      setAsOf(new Date().toISOString())
    }).catch(() => setNetWorth(null))
    listAccounts().then(setAccounts).catch(() => setAccounts([]))
  }, [])

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-2">Net Worth</h3>
        <div className="text-3xl font-bold">
          {netWorth !== null ? `$${netWorth.toFixed(2)}` : '—'}
        </div>
        {asOf && <div className="text-xs text-gray-500 mt-1">As of {new Date(asOf).toLocaleString()}</div>}
      </section>
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Accounts</h3>
        <ul className="space-y-2">
          {accounts.map(a => (
            <li key={a.id} className="flex justify-between">
              <span>{a.name}</span>
              <span className="font-medium">${a.balance.toFixed(2)}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
