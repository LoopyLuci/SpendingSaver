import { useCallback, useEffect, useState } from 'react'
import {
  getHubInfo, rotateHubKey, listSyncDevices, revokeSyncDevice,
  type HubInfo, type SyncDevice,
} from '../hooks/useBackend'

/**
 * Optional phone sync (see docs/sync.md). Nothing here is needed to use the desktop app:
 * it only shows the address and pairing key to type into the Android app, and lets you
 * revoke paired phones. The key and device list are only served to this machine.
 */
export default function SyncHub() {
  const [info, setInfo] = useState<HubInfo | null>(null)
  const [devices, setDevices] = useState<SyncDevice[]>([])
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [showKey, setShowKey] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    try {
      const [i, d] = await Promise.all([getHubInfo(), listSyncDevices()])
      setInfo(i)
      setDevices(d)
    } catch (e: any) {
      setError(e.message || String(e))
    }
  }, [])

  useEffect(() => { load() }, [load])

  const rotate = async () => {
    if (!window.confirm('Generate a new pairing key? Phones that are already paired keep working; new pairings need the new key.')) return
    try {
      await rotateHubKey()
      setMessage('Pairing key rotated.')
      await load()
    } catch (e: any) {
      setError(e.message || String(e))
    }
  }

  const revoke = async (d: SyncDevice) => {
    if (!window.confirm(`Revoke "${d.name}"? It will need to pair again to sync.`)) return
    try {
      await revokeSyncDevice(d.id)
      setMessage(`Revoked ${d.name}.`)
      await load()
    } catch (e: any) {
      setError(e.message || String(e))
    }
  }

  const address = info ? `${info.lan_ip || '<this PC\'s address>'}:${info.port}` : ''

  return (
    <div className="space-y-6">
      <div>
        <h3 className="text-xl font-bold text-gray-100">Phone Sync</h3>
        <p className="text-sm text-gray-500">
          Optional. The desktop app and the Android app each work on their own; pair a phone only if you want
          accounts, categories, transactions and budgets kept in step.
        </p>
      </div>

      {error && <div role="alert" className="bg-red-900/30 border border-red-700 text-red-200 rounded-lg px-4 py-3 text-sm">{error}</div>}
      {message && <div className="bg-emerald-900/30 border border-emerald-700 text-emerald-200 rounded-lg px-4 py-3 text-sm">{message}</div>}

      <section className="space-y-2">
        <h4 className="font-semibold text-gray-200">Pair a phone</h4>
        <p className="text-sm text-gray-400">
          In the Android app open Settings → Desktop sync, then enter:
        </p>
        <dl className="text-sm text-gray-200 grid grid-cols-[8rem_1fr] gap-y-1">
          <dt className="text-gray-500">Hub address</dt>
          <dd data-testid="hub-address">{address || '…'}</dd>
          <dt className="text-gray-500">Pairing key</dt>
          <dd className="flex items-center gap-2">
            <code data-testid="hub-key">{info?.key ? (showKey ? info.key : '••••••••••••') : '…'}</code>
            {info?.key && (
              <button className="text-xs underline text-gray-400" onClick={() => setShowKey(s => !s)}>
                {showKey ? 'Hide' : 'Show'}
              </button>
            )}
          </dd>
        </dl>
        <p className="text-xs text-gray-500">
          The backend listens on this PC only by default. To reach it from a phone, start it on your network
          (for example <code>uvicorn app.api:app --host 0.0.0.0 --port 8125</code>), allow the port in your
          firewall, and use a network you trust. Requests from other machines need a pairing or login token.
        </p>
        <button onClick={rotate} className="px-3 py-1.5 rounded-lg border border-gray-600 text-gray-200 text-sm">
          Rotate pairing key
        </button>
      </section>

      <section className="space-y-2">
        <h4 className="font-semibold text-gray-200">Paired phones</h4>
        {devices.length === 0 ? (
          <p className="text-sm text-gray-500">No phones paired.</p>
        ) : (
          <ul className="divide-y divide-gray-700">
            {devices.map(d => (
              <li key={d.id} className="flex items-center justify-between py-2 text-sm text-gray-200">
                <span>
                  {d.name}
                  <span className="text-gray-500">
                    {d.revoked ? ' — revoked' : d.last_seen ? ` — last seen ${d.last_seen}` : ' — never synced'}
                  </span>
                </span>
                {!d.revoked && (
                  <button onClick={() => revoke(d)} className="px-3 py-1 rounded-lg border border-red-700 text-red-300">
                    Revoke
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
