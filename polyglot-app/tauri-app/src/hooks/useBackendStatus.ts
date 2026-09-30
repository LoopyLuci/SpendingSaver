import { useState, useEffect, useCallback } from 'react'

type BackendStatus = 'online' | 'offline' | 'checking'

export function useBackendStatus(intervalMs = 5000) {
  const [python, setPython] = useState<BackendStatus>('checking')
  const [goStatus, setGo] = useState<BackendStatus>('checking')
  const [retrying, setRetrying] = useState(false)

  const check = useCallback(async () => {
    try {
      const res = await fetch('http://127.0.0.1:8125/health', { signal: AbortSignal.timeout(3000) })
      setPython(res.ok ? 'online' : 'offline')
    } catch {
      setPython('offline')
    }
    try {
      const res = await fetch('http://127.0.0.1:8126/health', { signal: AbortSignal.timeout(3000) })
      setGo(res.ok ? 'online' : 'offline')
    } catch {
      setGo('offline')
    }
    setRetrying(false)
  }, [])

  useEffect(() => {
    check()
    const id = setInterval(() => {
      setRetrying(true)
      check()
    }, intervalMs)
    return () => clearInterval(id)
  }, [check, intervalMs])

  const anyOffline = python === 'offline' || goStatus === 'offline'
  const queueSize = anyOffline ? 1 : 0

  return { python, go: goStatus, retrying, queueSize, refresh: check }
}
