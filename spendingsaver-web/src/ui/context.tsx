import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'
import type { DataStore, Snapshot } from '../storage/store'
import { todayYmd, type Ymd } from '../lib/dates'

const StoreContext = createContext<DataStore | null>(null)

export function StoreProvider({ store, children }: { store: DataStore; children: ReactNode }) {
  return <StoreContext.Provider value={store}>{children}</StoreContext.Provider>
}

export function useStore(): DataStore {
  const s = useContext(StoreContext)
  if (!s) throw new Error('StoreProvider missing')
  return s
}

export function useSnapshot(): Snapshot {
  const store = useStore()
  return useSyncExternalStore(store.subscribe, store.getSnapshot)
}

/** Today's date, refreshed when the tab becomes visible again (so a day change is picked up without a reload). */
export function useToday(): Ymd {
  const [today, setToday] = useState(() => todayYmd())
  useEffect(() => {
    const update = () => setToday(todayYmd())
    document.addEventListener('visibilitychange', update)
    const t = window.setInterval(update, 60_000)
    return () => { document.removeEventListener('visibilitychange', update); window.clearInterval(t) }
  }, [])
  return today
}

/** Currency used for totals: the setting, or the currency most of the accounts use. */
export function useBaseCurrency(): string {
  const { settings, live } = useSnapshot()
  return useMemo(() => {
    if (settings.currency && live.accounts.some((a) => a.currency.toUpperCase() === settings.currency.toUpperCase())) return settings.currency.toUpperCase()
    if (live.accounts.length === 0) return (settings.currency || 'USD').toUpperCase()
    const counts = new Map<string, number>()
    for (const a of live.accounts) counts.set(a.currency.toUpperCase(), (counts.get(a.currency.toUpperCase()) ?? 0) + 1)
    return [...counts.entries()].sort((a, b) => b[1] - a[1])[0][0]
  }, [settings.currency, live.accounts])
}

// ------------------------------------------------------------------ toasts
export interface ToastItem { id: number; message: string; action?: { label: string; run: () => void }; tone?: 'error' | 'ok' }

interface ToastApi { toasts: ToastItem[]; show: (message: string, opts?: { action?: ToastItem['action']; tone?: ToastItem['tone'] }) => void; dismiss: (id: number) => void }
const ToastContext = createContext<ToastApi | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const seq = useRef(0)
  const dismiss = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), [])
  const show = useCallback<ToastApi['show']>((message, opts = {}) => {
    const id = ++seq.current
    setToasts((t) => [...t.slice(-2), { id, message, ...opts }])
    window.setTimeout(() => dismiss(id), opts.action ? 8000 : 4500)
  }, [dismiss])
  const value = useMemo(() => ({ toasts, show, dismiss }), [toasts, show, dismiss])
  return <ToastContext.Provider value={value}>{children}</ToastContext.Provider>
}

export function useToast() {
  const t = useContext(ToastContext)
  if (!t) throw new Error('ToastProvider missing')
  return t
}
