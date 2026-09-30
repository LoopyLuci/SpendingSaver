import { useState, useEffect } from 'react'
import { db, Account, Transaction } from '../db/db'
import { importCSV } from '../services/csv'
import { exportCSV } from '../services/csv'

export default function ImportExport() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState<number>(0)
  const [importStatus, setImportStatus] = useState('')
  const [allTransactions, setAllTransactions] = useState<Transaction[]>([])

  useEffect(() => {
    const load = async () => {
      setAccounts(await db.accounts.toArray())
      setAllTransactions(await db.transactions.toArray())
    }
    load()
  }, [])

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !selectedAccountId) {
      setImportStatus('Please select an account and a file.')
      return
    }
    try {
      const count = await importCSV(file, selectedAccountId)
      setImportStatus(`Successfully imported ${count} transactions.`)
      setAllTransactions(await db.transactions.toArray())
    } catch (err) {
      setImportStatus(`Error: ${err instanceof Error ? err.message : 'Unknown error'}`)
    }
  }

  const handleExportAll = async () => {
    const csv = await exportCSV(allTransactions)
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'spendingsaver_all_transactions.csv'
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">Import & Export</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <h2 className="text-lg font-semibold mb-4">Import CSV</h2>
          <p className="text-sm text-gray-500 mb-2">CSV columns: Date, Description, Amount (negative for expense)</p>
          <select
            className="border rounded p-2 w-full mb-2 dark:bg-gray-700"
            value={selectedAccountId}
            onChange={e => setSelectedAccountId(Number(e.target.value))}
          >
            <option value={0}>Select account...</option>
            {accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
          <input
            type="file"
            accept=".csv"
            onChange={handleFileChange}
            className="mb-2"
          />
          {importStatus && <p className="text-sm mt-2">{importStatus}</p>}
        </div>

        <div className="bg-white dark:bg-gray-800 p-5 rounded-lg shadow">
          <h2 className="text-lg font-semibold mb-4">Export</h2>
          <p className="text-sm text-gray-500 mb-4">Export all transactions to CSV.</p>
          <button
            onClick={handleExportAll}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            Export All Transactions
          </button>
        </div>
      </div>
    </div>
  )
}
