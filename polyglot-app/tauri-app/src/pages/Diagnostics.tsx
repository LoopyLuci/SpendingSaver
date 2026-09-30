import { useState, useEffect } from 'react'
import { apiHealth, goHealth, listConnectors, getHostInfo } from '../hooks/useBackend'

export default function Diagnostics() {
  const [backend, setBackend] = useState<any>(null)
  const [go, setGo] = useState<any>(null)
  const [connectors, setConnectors] = useState<any[]>([])
  const [host, setHost] = useState<any>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  async function refresh() {
    setError(null)
    try {
      const [b, g, cs, h] = await Promise.all([
        apiHealth().catch((e) => ({ error: e.message })),
        goHealth(),
        listConnectors().catch(() => []),
        getHostInfo(),
      ])
      setBackend(b)
      setGo(g)
      setConnectors(cs)
      setHost(h)
    } catch (e: any) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh() }, [])

  const rows = [
    { key: 'Backend', value: backend?.status === 'ok' ? '✅ online' : `⚠ ${backend?.error || 'unknown'}`, status: backend?.status === 'ok' ? 'good' : 'bad' },
    { key: 'Go service', value: go?.status === 'ok' || go?.status === 'online' ? `✅ online ${go?.service || ''}` : `⚠ ${go?.status || 'unknown'}`, status: (go?.status === 'ok' || go?.status === 'online') ? 'good' : 'bad' },
    { key: 'Self-hosted hub', value: host && !host.error ? `${host.lan_ip}:${host.port} · key ${host.key}` : '⚠ not reachable', status: host && !host.error ? 'good' : 'bad' },
    { key: 'Connectors', value: `${connectors.length} installed (${connectors.map((c) => c.name).join(', ')})`, status: connectors.length > 0 ? 'good' : 'warn' },
    { key: 'Version', value: host?.version ? `backend v${host.version}` : '0.1.0', status: 'good' },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold">Diagnostics</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400">Live status of every SpendingSaver system.</p>
        </div>
        <button onClick={refresh} disabled={loading}
          className="px-3 py-1.5 text-sm bg-gray-800 hover:bg-gray-900 dark:bg-gray-700 dark:hover:bg-gray-600 text-white rounded-lg disabled:opacity-50">
          {loading ? 'Refreshing…' : '↻ Refresh'}
        </button>
      </div>

      {error && <div className="bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 text-sm rounded-lg px-4 py-3">{error}</div>}

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow">
        <div className="border-b border-gray-100 dark:border-gray-700 px-6 py-4"><h3 className="font-semibold">System health</h3></div>
        <div className="divide-y divide-gray-100 dark:divide-gray-700">
          {rows.map((r) => (
            <div key={r.key} className="px-6 py-3 flex items-center justify-between">
              <span className="text-sm font-medium text-gray-600 dark:text-gray-300">{r.key}</span>
              <span className={`text-sm ${r.status === 'good' ? 'text-emerald-600 dark:text-emerald-400' : r.status === 'warn' ? 'text-amber-600 dark:text-amber-400' : 'text-red-600 dark:text-red-400'}`}>
                {r.value}
              </span>
            </div>
          ))}
        </div>
      </section>

      {host && !host.error && (
        <section className="bg-white dark:bg-gray-800 rounded-lg shadow">
          <div className="border-b border-gray-100 dark:border-gray-700 px-6 py-4"><h3 className="font-semibold">Self-hosted server</h3></div>
          <div className="px-6 py-4 grid gap-3 sm:grid-cols-2">
            <div className="text-sm"><span className="text-gray-500 dark:text-gray-400">Name</span><div className="font-medium">{host.name}</div></div>
            <div className="text-sm"><span className="text-gray-500 dark:text-gray-400">LAN IP</span><div className="font-medium">{host.lan_ip}:{host.port}</div></div>
            <div className="text-sm"><span className="text-gray-500 dark:text-gray-400">Connection key</span><div className="font-mono font-medium text-blue-600 dark:text-blue-400">{host.key}</div></div>
            <div className="text-sm"><span className="text-gray-500 dark:text-gray-400">Mode</span><div className="font-medium">{host.api || 'self-hosted'}</div></div>
          </div>
        </section>
      )}
    </div>
  )
}