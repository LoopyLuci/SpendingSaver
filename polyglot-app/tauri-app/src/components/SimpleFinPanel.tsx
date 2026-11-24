import { useCallback, useEffect, useState } from 'react'
import {
  getSimpleFinStatus, connectSimpleFin, syncSimpleFin, disconnectSimpleFin, type SimpleFinStatus,
} from '../hooks/useBackend'

/**
 * Optional: pull US bank accounts and transactions through SimpleFIN Bridge, a service you subscribe to
 * yourself (about $15/year). You paste a one-time setup token; the credential stays on this PC, read-only.
 * Nothing here is needed to use SpendingSaver. See docs/bank-linking.md.
 */
export default function SimpleFinPanel({ onImported }: { onImported?: () => void }) {
  const [status, setStatus] = useState<SimpleFinStatus | null>(null)
  const [token, setToken] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const load = useCallback(async () => {
    try { setStatus(await getSimpleFinStatus()) } catch { setStatus(null) } // older backend: stay hidden
  }, [])
  useEffect(() => { load() }, [load])

  const run = async (fn: () => Promise<string | void>) => {
    setBusy(true); setError(null); setMessage(null)
    try {
      const m = await fn()
      if (m) setMessage(m)
    } catch (e: any) {
      setError(e.message || String(e))
    } finally {
      setBusy(false)
      await load()
    }
  }

  if (!status) return null

  return (
    <section className="bg-white dark:bg-gray-800 rounded-lg shadow" data-testid="simplefin-panel">
      <div className="border-b border-gray-100 dark:border-gray-700 px-6 py-4">
        <h3 className="font-semibold">Link a US bank (SimpleFIN, optional)</h3>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
          Read-only access through SimpleFIN Bridge, which you subscribe to yourself. Your bank login stays with
          SimpleFIN and the access key stays on this PC; transactions come straight here, not through any SpendingSaver server.
        </p>
      </div>
      <div className="px-6 py-4 space-y-3 text-sm">
        {error && <div role="alert" className="text-red-600 dark:text-red-300">{error}</div>}
        {message && <div className="text-emerald-700 dark:text-emerald-300">{message}</div>}
        {status.connected ? (
          <>
            <p>
              Connected.{' '}
              {status.last_sync ? `Last updated ${new Date(status.last_sync).toLocaleString()}.` : 'Not updated yet.'}
              {status.last_result && ` Last result: ${status.last_result.added} new, ${status.last_result.accounts} new account(s).`}
            </p>
            {status.last_error && <p className="text-amber-700 dark:text-amber-300">Bridge said: {status.last_error}</p>}
            <div className="flex gap-2">
              <button disabled={busy} data-testid="sf-sync"
                onClick={() => run(async () => {
                  const r = await syncSimpleFin()
                  onImported?.()
                  return `Updated: ${r.result?.added ?? 0} new transaction(s). SimpleFIN allows about one update an hour.`
                })}
                className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg disabled:opacity-50">
                {busy ? 'Updating…' : 'Update now'}
              </button>
              <button disabled={busy}
                onClick={() => {
                  if (!window.confirm('Forget the SimpleFIN key on this PC? Imported transactions stay. To fully cut access, also disable it in your SimpleFIN Bridge account.')) return
                  run(async () => (await disconnectSimpleFin()).note)
                }}
                className="px-3 py-1.5 border border-gray-300 dark:border-gray-600 rounded-lg">Disconnect</button>
            </div>
          </>
        ) : (
          <>
            <ol className="list-decimal ml-5 text-gray-600 dark:text-gray-300 space-y-1">
              <li>Subscribe at SimpleFIN Bridge and link your bank there.</li>
              <li>Create a <em>setup token</em> and paste it below. It works once.</li>
            </ol>
            <input value={token} onChange={e => setToken(e.target.value)} placeholder="Setup token" data-testid="sf-token"
              autoComplete="off" spellCheck={false}
              className="w-full border rounded p-2 dark:bg-gray-700 dark:border-gray-600 font-mono text-xs" />
            <button disabled={busy || !token.trim()} data-testid="sf-connect"
              onClick={() => run(async () => { await connectSimpleFin(token.trim()); setToken(''); return 'Connected. Press Update now to fetch your accounts.' })}
              className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg disabled:opacity-50">Connect</button>
          </>
        )}
      </div>
    </section>
  )
}
