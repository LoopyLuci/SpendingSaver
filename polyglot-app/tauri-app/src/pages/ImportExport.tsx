import { useEffect, useState } from 'react'
import Papa from 'papaparse'
import { listCategories, importTransactions } from '../hooks/useBackend'

export default function ImportExport() {
  const [importCsv, setImportCsv] = useState('')
  const [importResult, setImportResult] = useState<any>(null)
  const [importError, setImportError] = useState<string | null>(null)
  const [exportError, setExportError] = useState<string | null>(null)
  const [importing, setImporting] = useState(false)
  const [categories, setCategories] = useState<Array<{ id: string; name: string }>>([])

  useEffect(() => {
    listCategories().then(setCategories).catch(() => {})
  }, [])

  const handleImport = async () => {
    setImportError(null)
    setImportResult(null)
    try {
      const parsed = Papa.parse(importCsv, { header: true, dynamicTyping: true, skipEmptyLines: true })
      if (parsed.errors.length) {
        setImportError(parsed.errors.map((e: any) => e.message).join(', '))
        return
      }
      const rows = parsed.data.map((row: any) => {
        const categoryName = String(row.Category || row.category || '').trim()
        const category = categories.find(c => c.name.toLowerCase() === categoryName.toLowerCase())
        return {
          account_id: String(row.AccountID || row.account_id || ''),
          amount: typeof row.Amount !== 'undefined' ? Number(row.Amount) : Number(row.amount || 0),
          description: String(row.Description || row.description || ''),
          date: String(row.Date || row.date || ''),
          category_id: category?.id || (row.CategoryID ? String(row.CategoryID) : ''),
          notes: row.Notes ? String(row.Notes) : (row.notes ? String(row.notes) : ''),
        }
      })
      setImportResult({ parsed: rows.length, preview: rows.slice(0, 5), raw: rows })
    } catch (e: any) {
      setImportError(e.message)
    }
  }

  const confirmImport = async () => {
    if (!importResult?.raw?.length) return
    setImporting(true)
    setImportError(null)
    try {
      const res = await importTransactions(importResult.raw)
      setImportResult({ ...importResult, result: res })
    } catch (e: any) {
      setImportError(e.message)
    } finally {
      setImporting(false)
    }
  }

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      setImportCsv(reader.result as string)
      setImportResult(null)
      setImportError(null)
    }
    reader.readAsText(file)
  }

  const handleExport = async () => {
    setExportError(null)
    try {
      const USE_TAURI = typeof window !== 'undefined' && !!(window as any).__TAURI__?.invoke
      if (!USE_TAURI) {
        setExportError('Export requires Tauri desktop mode')
        return
      }
      const res = await fetch('http://127.0.0.1:8125/api/transactions')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      if (!data.length) {
        setExportError('No transactions to export')
        return
      }
      const csv = Papa.unparse(data)
      const path = await (window as any).__TAURI__.invoke('save_file_dialog', {
        filters: [{ name: 'CSV', extensions: ['csv'] }],
        defaultPath: 'transactions.csv',
      })
      if (!path) return
      await (window as any).__TAURI__.invoke('write_text_file', { path, text: csv })
    } catch (e: any) {
      setExportError(e.message)
    }
  }

  const handleExportJSON = async () => {
    setExportError(null)
    try {
      const USE_TAURI = typeof window !== 'undefined' && !!(window as any).__TAURI__?.invoke
      if (!USE_TAURI) {
        setExportError('Export requires Tauri desktop mode')
        return
      }
      const [accounts, categories, transactions, budgets] = await Promise.all([
        fetch('http://127.0.0.1:8125/api/accounts').then(r => r.ok ? r.json() : []),
        fetch('http://127.0.0.1:8125/api/categories').then(r => r.ok ? r.json() : []),
        fetch('http://127.0.0.1:8125/api/transactions').then(r => r.ok ? r.json() : []),
        fetch('http://127.0.0.1:8125/api/budgets').then(r => r.ok ? r.json() : []),
      ])
      const data = { accounts, categories, transactions, budgets, exported_at: new Date().toISOString() }
      const text = JSON.stringify(data, null, 2)
      const path = await (window as any).__TAURI__.invoke('save_file_dialog', {
        filters: [{ name: 'JSON', extensions: ['json'] }],
        defaultPath: 'spendingsaver-backup.json',
      })
      if (!path) return
      await (window as any).__TAURI__.invoke('write_text_file', { path, text })
    } catch (e: any) {
      setExportError(e.message)
    }
  }

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Import CSV</h3>
        <div className="space-y-3">
          <input type="file" accept=".csv,text/csv" onChange={handleFile} className="block w-full border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <textarea
            className="w-full border rounded p-3 dark:bg-gray-700 dark:border-gray-600"
            rows={6}
            placeholder="Paste CSV here or choose a file..."
            value={importCsv}
            onChange={e => setImportCsv(e.target.value)}
          />
          <button onClick={handleImport} className="px-4 py-2 bg-blue-600 text-white rounded">Parse CSV</button>
          {importResult && !importResult.result && (
            <button onClick={confirmImport} disabled={importing} className="px-4 py-2 bg-green-600 text-white rounded">
              {importing ? 'Importing...' : 'Confirm import'}
            </button>
          )}
          {importResult?.result && (
            <div className="text-sm text-gray-700 dark:text-gray-200">Created: {importResult.result.created} {importResult.result.errors?.length ? `• Errors: ${importResult.result.errors.length}` : ''}</div>
          )}
          {importError && <div className="text-red-600">{importError}</div>}
          {importResult && (
            <div className="space-y-2">
              <div className="text-sm text-gray-600 dark:text-gray-300">Rows: {importResult.parsed} • Fields: name/amount/description/date/category</div>
              <pre className="p-3 bg-gray-50 dark:bg-gray-700 rounded text-sm overflow-auto">
                {JSON.stringify(importResult.preview, null, 2)}
              </pre>
            </div>
          )}
        </div>
      </section>
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-2">Export</h3>
        {exportError && <div className="text-red-600 text-sm mb-2">{exportError}</div>}
        <div className="flex gap-2">
          <button onClick={handleExport} className="px-4 py-2 bg-blue-600 text-white rounded">Export transactions as CSV</button>
          <button onClick={handleExportJSON} className="px-4 py-2 bg-blue-600 text-white rounded">Export all data as JSON</button>
        </div>
      </section>
    </div>
  )
}
