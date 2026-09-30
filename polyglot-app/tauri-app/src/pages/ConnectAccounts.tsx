import { useEffect, useState } from 'react'
import { listConnectors, connectConnector, fetchConnector, listAccounts, previewCsvImport } from '../hooks/useBackend'

interface Connector {
  name: string
  description: string
  category: string
  supports_oauth?: boolean
}

const CATEGORY_LABEL: Record<string, string> = {
  file: 'File import',
  manual: 'Manual',
  bank: 'Bank linking',
}

export default function ConnectAccounts() {
  const [connectors, setConnectors] = useState<Connector[]>([])
  const [accounts, setAccounts] = useState<any[]>([])
  const [error, setError] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [csvOpen, setCsvOpen] = useState(false)
  const [csvContent, setCsvContent] = useState('')
  const [csvPreviewing, setCsvPreviewing] = useState(false)
  const [csvImporting, setCsvImporting] = useState(false)
  const [csvItems, setCsvItems] = useState<any[]>([])
  const [csvError, setCsvError] = useState<string | null>(null)

  useEffect(() => {
    listConnectors()
      .then(setConnectors)
      .catch((e) => setError(e.message))
    listAccounts()
      .then(setAccounts)
      .catch(() => {})
  }, [])

  async function doFetch(name: string, account_id: string | undefined, params: Record<string, any> = {}) {
    setMsg(null)
    setError(null)
    try {
      const res = await fetchConnector(name, params, account_id)
      setMsg(`${name}: fetched ${res.fetched ?? 0} transaction(s)${res.items?.length ? `, created ${res.items.length}` : ''}`)
    } catch (e: any) {
      setError(`${name}: ${e.message}`)
    }
  }

  async function doConnect(name: string) {
    setError(null)
    setMsg(null)
    try {
      const res = await connectConnector(name, {})
      setMsg(`${name}: ${res.message || 'connected'}`)
    } catch (e: any) {
      setError(`${name}: ${e.message}`)
    }
  }

  function openCsvImport() {
    setCsvOpen(true)
    setCsvItems([])
    setCsvError(null)
  }

  async function previewCsv() {
    setCsvError(null)
    if (!csvContent.trim()) {
      setCsvError('Paste CSV content first.')
      return
    }
    setCsvPreviewing(true)
    try {
      const res = await previewCsvImport(csvContent, accounts[0]?.id)
      setCsvItems(Array.isArray(res.items) ? res.items : [])
    } catch (e: any) {
      setCsvError(`Preview failed: ${e.message}`)
    } finally {
      setCsvPreviewing(false)
    }
  }

  async function confirmAndImport() {
    setCsvError(null)
    setCsvImporting(true)
    try {
      // Re-run the fetch WITHOUT dry_run so records are persisted.
      const res = await fetchConnector('csv', { content: csvContent }, accounts[0]?.id, false)
      setMsg(`CSV import: created ${res.items?.length ?? 0} transaction(s)`)
      setCsvOpen(false)
      setCsvContent('')
      setCsvItems([])
      listAccounts().then(setAccounts).catch(() => {})
    } catch (e: any) {
      setCsvError(`Import failed: ${e.message}`)
    } finally {
      setCsvImporting(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold">Connect Accounts &amp; Import</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Bring in transaction data from CSV, OFX/QIF bank files, or link a bank via Open Banking.
          </p>
        </div>
      </div>

      {error && <div className="bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 text-sm rounded-lg px-4 py-3">{error}</div>}
      {msg && <div className="bg-emerald-50 dark:bg-emerald-900/30 border border-emerald-200 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300 text-sm rounded-lg px-4 py-3">{msg}</div>}

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow">
        <div className="border-b border-gray-100 dark:border-gray-700 px-6 py-4">
          <h3 className="font-semibold">Available data sources</h3>
        </div>
        <div className="divide-y divide-gray-100 dark:divide-gray-700">
          {connectors.map((c) => (
            <div key={c.name} className="px-6 py-4 flex flex-wrap items-center gap-4">
              <div className="flex-1 min-w-[200px]">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{c.name}</span>
                  <span className="text-xs bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded-full">
                    {CATEGORY_LABEL[c.category] || c.category}
                  </span>
                  {c.supports_oauth && <span className="text-xs bg-blue-100 dark:bg-blue-900 px-2 py-0.5 rounded-full text-blue-700 dark:text-blue-300">OAuth</span>}
                </div>
                <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{c.description}</p>
              </div>
              <div className="flex items-center gap-2">
                {c.category === 'bank' && (
                  <button
                    onClick={() => doConnect(c.name)}
                    className="px-3 py-1.5 text-sm bg-blue-600 hover:bg-blue-700 text-white rounded-lg"
                  >
                    Link account
                  </button>
                )}
                {(c.name === 'csv' || c.name === 'ofx') && (
                  <button
                    onClick={() => (c.name === 'csv' ? openCsvImport() : doFetch(c.name, accounts[0]?.id))}
                    className="px-3 py-1.5 text-sm bg-gray-800 hover:bg-gray-900 dark:bg-gray-700 dark:hover:bg-gray-600 text-white rounded-lg"
                    title={accounts.length === 0 ? 'Add an account first to import into' : undefined}
                    disabled={accounts.length === 0}
                  >
                    {c.name === 'csv' ? 'Import CSV' : 'Import OFX/QIF'}
                  </button>
                )}
              </div>
            </div>
          ))}
          {connectors.length === 0 && !error && <div className="px-6 py-8 text-center text-gray-500">Loading data sources…</div>}
        </div>
      </section>

      {csvOpen && (
        <section className="bg-white dark:bg-gray-800 rounded-lg shadow">
          <div className="border-b border-gray-100 dark:border-gray-700 px-6 py-4 flex items-center justify-between">
            <h3 className="font-semibold">Import CSV</h3>
            <button onClick={() => setCsvOpen(false)} className="text-sm text-gray-500 hover:text-gray-700 dark:hover:text-gray-300">Close</button>
          </div>
          <div className="px-6 py-4 space-y-4">
            <textarea
              value={csvContent}
              onChange={e => { setCsvContent(e.target.value); setCsvItems([]) }}
              rows={8}
              placeholder="Paste CSV content here (date,amount,description[,category])…"
              className="w-full border rounded p-2 dark:bg-gray-700 dark:border-gray-600 text-sm font-mono"
            />
            {csvError && <div className="text-red-600 text-sm">{csvError}</div>}
            <div className="flex items-center gap-3">
              <button
                onClick={previewCsv}
                disabled={csvPreviewing}
                className="px-3 py-1.5 text-sm bg-blue-600 hover:bg-blue-700 text-white rounded-lg disabled:opacity-50"
              >
                {csvPreviewing ? 'Previewing…' : 'Preview'}
              </button>
              {csvItems.length > 0 && (
                <span className="text-sm text-gray-500">{csvItems.length} row(s) parsed — review then confirm to import.</span>
              )}
            </div>

            {csvItems.length > 0 && (
              <>
                <div className="overflow-x-auto border border-gray-100 dark:border-gray-700 rounded-lg">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-50 dark:bg-gray-700 text-left">
                      <tr>
                        <th className="px-3 py-2 font-medium">Date</th>
                        <th className="px-3 py-2 font-medium">Description</th>
                        <th className="px-3 py-2 font-medium text-right">Amount</th>
                        <th className="px-3 py-2 font-medium">Category</th>
                      </tr>
                    </thead>
                    <tbody>
                      {csvItems.map((item, idx) => (
                        <tr key={idx} className="border-t border-gray-100 dark:border-gray-700">
                          <td className="px-3 py-2">{item.date}</td>
                          <td className="px-3 py-2">{item.description}</td>
                          <td className="px-3 py-2 text-right">{(typeof item.amount === 'number' ? item.amount : parseFloat(item.amount) || 0).toFixed(2)}</td>
                          <td className="px-3 py-2">{item.category || '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <button
                  onClick={confirmAndImport}
                  disabled={csvImporting}
                  className="px-3 py-1.5 text-sm bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg disabled:opacity-50"
                >
                  {csvImporting ? 'Importing…' : 'Confirm and Import'}
                </button>
              </>
            )}
          </div>
        </section>
      )}

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow">
        <div className="border-b border-gray-100 dark:border-gray-700 px-6 py-4">
          <h3 className="font-semibold">Import to an account</h3>
        </div>
        <div className="px-6 py-4">
          {accounts.length === 0 ? (
            <p className="text-sm text-gray-500">
              No accounts yet. <a href="#/accounts" className="text-blue-600 hover:underline">Add an account</a> first so imported transactions have a home.
            </p>
          ) : (
            <p className="text-sm text-gray-500">Importing uses the first account ({accounts[0].name}). Add a dedicated account per bank for cleaner categorization.</p>
          )}
        </div>
      </section>
    </div>
  )
}