import { useCallback, useEffect, useState } from 'react'

interface AndroidDevice {
  serial: string
  model: string
  state: string
}

interface HostInfo {
  name?: string
  lan_ip?: string
  port?: number
  key?: string
}

type HostInfoRaw = Record<string, unknown>

function useTauri() {
  return typeof window !== 'undefined' && !!(window as any)?.__TAURI__?.invoke
    ? (window as any).__TAURI__ : null
}

export default function AndroidDevices() {
  const tauri = useTauri()
  const [devices, setDevices] = useState<AndroidDevice[]>([])
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Pair form
  const [pairHost, setPairHost] = useState('')
  const [pairKey, setPairKey] = useState('')

  // Server connection info
  const [hostInfo, setHostInfo] = useState<HostInfo | null>(null)
  const [busySerial, setBusySerial] = useState<string | null>(null)

  const req = (cmd: string, args: Record<string, unknown> = {}) => {
    if (!tauri) throw new Error('Requires Tauri desktop mode')
    return tauri.invoke(cmd, args)
  }

  const refresh = useCallback(async () => {
    if (!tauri) {
      setError('Requires Tauri desktop mode')
      return
    }
    setLoading(true)
    setError(null)
    try {
      const result = (await req('list_android_devices')) as AndroidDevice[]
      setDevices(result || [])
    } catch (e: any) {
      setError(e.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [tauri])

  const loadHostInfo = useCallback(async () => {
    if (!tauri) return
    try {
      const result = (await req('get_host_info')) as string
      const parsed = JSON.parse(result) as HostInfoRaw
      setHostInfo({
        name: String(parsed.name ?? ''),
        lan_ip: String(parsed.lan_ip ?? ''),
        port: parsed.port as number | undefined,
        key: String(parsed.key ?? ''),
      })
    } catch (e: any) {
      setMessage(null)
      setError(e.message || String(e))
    }
  }, [tauri])

  useEffect(() => {
    refresh()
    loadHostInfo()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tauri])

  const install = async (serial: string) => {
    setBusySerial(serial)
    setError(null)
    try {
      setMessage(await req('install_apk', { serial }))
    } catch (e: any) {
      setError(e.message || String(e))
    } finally {
      setBusySerial(null)
    }
  }

  const send = async () => {
    setError(null)
    try {
      setMessage(await req('send_apk'))
    } catch (e: any) {
      setError(e.message || String(e))
    }
  }

  const pair = async (serial: string) => {
    if (!pairHost.trim() || !pairKey.trim()) {
      setError('Enter a server host and pairing key first')
      return
    }
    setBusySerial(serial)
    setError(null)
    try {
      setMessage(
        await req('pair_android', { serial, serverHost: pairHost, pairKey })
      )
    } catch (e: any) {
      setError(e.message || String(e))
    } finally {
      setBusySerial(null)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-xl font-bold text-gray-100">Android Devices</h3>
          <p className="text-sm text-gray-500">
            Discover, pair, and install the SpendingSaver APK over ADB.
          </p>
        </div>
        <button
          onClick={refresh}
          className="px-4 py-2 rounded-lg btn-accent text-white"
        >
          ⟳ Refresh
        </button>
      </div>

      {error && (
        <div className="bg-red-900/30 border border-red-700 text-red-200 rounded-lg px-4 py-3 text-sm">
          {error}
        </div>
      )}
      {message && (
        <div className="bg-emerald-900/30 border border-emerald-700 text-emerald-200 rounded-lg px-4 py-3 text-sm whitespace-pre-wrap">
          {message}
        </div>
      )}

      {/* Server connection key */}
      <section className="bg-white dark:bg-gray-800 rounded-2xl shadow p-6">
        <div className="flex items-center justify-between mb-3">
          <h4 className="text-lg font-semibold">Server Connection Key</h4>
          <button
            onClick={loadHostInfo}
            className="px-3 py-1.5 rounded-lg bg-gray-700 text-gray-200 text-sm"
          >
            Fetch
          </button>
        </div>
        {hostInfo && hostInfo.lan_ip ? (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-sm">
            <div className="bg-gray-900 dark:bg-gray-700 rounded-lg p-3">
              <div className="text-gray-500 text-xs">LAN IP</div>
              <div className="font-mono text-gray-100">
                {hostInfo.lan_ip}
                {hostInfo.port ? `:${hostInfo.port}` : ''}
              </div>
            </div>
            <div className="bg-gray-900 dark:bg-gray-700 rounded-lg p-3 sm:col-span-2">
              <div className="text-gray-500 text-xs">Pairing Key</div>
              <div className="font-mono text-gray-100">
                {hostInfo.key || '—'}
              </div>
            </div>
          </div>
        ) : (
          <p className="text-sm text-gray-500">
            {loading ? 'Loading…' : 'No connection info yet — fetch from the backend.'}
          </p>
        )}
      </section>

      {/* Connected devices */}
      <section className="bg-white dark:bg-gray-800 rounded-2xl shadow p-6">
        <h4 className="text-lg font-semibold mb-3">Connected Devices</h4>
        {devices.length === 0 ? (
          <p className="text-sm text-gray-500">
            {loading
              ? 'Scanning…'
              : 'No devices connected. Plug in a phone or tablet over USB.'}
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="text-gray-500 border-b border-gray-700">
                  <th className="py-2 pr-4">Serial</th>
                  <th className="py-2 pr-4">Model</th>
                  <th className="py-2 pr-4">State</th>
                  <th className="py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {devices.map((d) => (
                  <tr key={d.serial} className="border-b border-gray-800">
                    <td className="py-2 pr-4 font-mono text-gray-100">{d.serial}</td>
                    <td className="py-2 pr-4 text-gray-200">{d.model}</td>
                    <td className="py-2 pr-4">
                      <span
                        className={`inline-block px-2 py-0.5 rounded-full text-xs ${
                          d.state === 'device'
                            ? 'bg-emerald-900/40 text-emerald-300'
                            : 'bg-yellow-900/40 text-yellow-300'
                        }`}
                      >
                        {d.state}
                      </span>
                    </td>
                    <td className="py-2 text-right space-x-2">
                      <button
                        onClick={() => install(d.serial)}
                        disabled={busySerial === d.serial}
                        className="px-3 py-1.5 rounded-lg bg-blue-600 text-white text-sm disabled:opacity-50"
                      >
                        Install APK
                      </button>
                      <button
                        onClick={() => pair(d.serial)}
                        disabled={busySerial === d.serial}
                        className="px-3 py-1.5 rounded-lg bg-gray-700 text-gray-200 text-sm disabled:opacity-50"
                      >
                        Pair
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pair form */}
        <div className="mt-5 flex flex-col sm:flex-row gap-3">
          <input
            value={pairHost}
            onChange={(e) => setPairHost(e.target.value)}
            placeholder="Server host (e.g. 192.168.1.50)"
            className="border rounded-lg px-3 py-2 dark:bg-gray-700 dark:border-gray-600 text-sm flex-1"
          />
          <input
            value={pairKey}
            onChange={(e) => setPairKey(e.target.value)}
            placeholder="Pairing key"
            className="border rounded-lg px-3 py-2 dark:bg-gray-700 dark:border-gray-600 text-sm flex-1"
          />
          <button
            onClick={send}
            className="px-4 py-2 rounded-lg btn-accent text-white text-sm"
          >
            Send APK
          </button>
        </div>
      </section>
    </div>
  )
}