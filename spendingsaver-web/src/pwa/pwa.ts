import { useSyncExternalStore } from 'react'

export interface PwaState {
  /** A service worker is controlling this page (so it can work offline). */
  offlineReady: boolean
  /** A new version finished installing and is waiting; applying it reloads the page. */
  updateReady: boolean
  /** Why registration is unavailable, if it is. */
  unsupported: string | null
  canInstall: boolean
  installed: boolean
  ios: boolean
  /** Briefly true right after the first install finished caching the app. */
  justCached: boolean
}

let state: PwaState = {
  offlineReady: false, updateReady: false, unsupported: null, canInstall: false, installed: false, ios: false, justCached: false,
}
const listeners = new Set<() => void>()
const set = (patch: Partial<PwaState>) => { state = { ...state, ...patch }; listeners.forEach((l) => l()) }

export const usePwa = () => useSyncExternalStore((l) => { listeners.add(l); return () => { listeners.delete(l) } }, () => state)

interface InstallEvent extends Event { prompt(): Promise<void>; userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }> }
let deferred: InstallEvent | null = null
let waiting: ServiceWorker | null = null
let reloading = false
let registration: ServiceWorkerRegistration | null = null

export function isStandalone(): boolean {
  return (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) || (navigator as unknown as { standalone?: boolean }).standalone === true
}

export function isIos(): boolean {
  const ua = navigator.userAgent
  return /iPhone|iPad|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

/** The app's root URL, derived from the entry script so it is right under any sub-folder (assets/ is one level down). */
export function appRoot(): URL {
  return new URL('../', import.meta.url)
}

export function initPwa() {
  set({ ios: isIos(), installed: isStandalone() })
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault()
    deferred = e as InstallEvent
    set({ canInstall: true })
  })
  window.addEventListener('appinstalled', () => { deferred = null; set({ canInstall: false, installed: true }) })

  if (!('serviceWorker' in navigator)) { set({ unsupported: 'This browser has no service worker support, so the app cannot work offline.' }); return }
  if (!window.isSecureContext) { set({ unsupported: 'Offline support needs HTTPS (or localhost).' }); return }
  if (!import.meta.env.PROD) { set({ unsupported: 'Service worker is only registered in production builds.' }); return }

  const hadController = !!navigator.serviceWorker.controller
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (reloading) window.location.reload() // only after the user pressed "Reload"
    else set({ offlineReady: true })
  })
  const root = appRoot()
  navigator.serviceWorker
    .register(new URL('sw.js', root).href, { scope: root.pathname, updateViaCache: 'none' })
    .then((reg) => {
      registration = reg
      set({ offlineReady: !!navigator.serviceWorker.controller })
      const track = (w: ServiceWorker | null) => {
        if (!w) return
        w.addEventListener('statechange', () => {
          if (w.state === 'installed' && navigator.serviceWorker.controller) { waiting = w; set({ updateReady: true }) }
          if (w.state === 'activated' && !hadController) { set({ offlineReady: true, justCached: true }); window.setTimeout(() => set({ justCached: false }), 8000) }
        })
      }
      if (reg.waiting && navigator.serviceWorker.controller) { waiting = reg.waiting; set({ updateReady: true }) }
      track(reg.installing)
      reg.addEventListener('updatefound', () => track(reg.installing))
      const check = () => { reg.update().catch(() => { /* offline: try again later */ }) }
      document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') check() })
      window.setInterval(check, 60 * 60 * 1000)
    })
    .catch((e: unknown) => set({ unsupported: `Could not start the service worker: ${e instanceof Error ? e.message : 'unknown error'}` }))
}

export function checkForUpdate(): Promise<void> {
  return registration ? registration.update().then(() => undefined) : Promise.resolve()
}

/** Activate the waiting worker and reload. Only ever called from a user action. */
export function applyUpdate() {
  if (!waiting) { window.location.reload(); return }
  reloading = true
  waiting.postMessage({ type: 'SKIP_WAITING' })
}

export function dismissCached() { set({ justCached: false }) }

export async function promptInstall(): Promise<'accepted' | 'dismissed' | 'unavailable'> {
  if (!deferred) return 'unavailable'
  const e = deferred
  deferred = null
  set({ canInstall: false })
  await e.prompt()
  const { outcome } = await e.userChoice
  return outcome
}

/** Ask the active worker which build it is (shown in Settings). */
export function getSwVersion(): Promise<string | null> {
  return new Promise((resolve) => {
    const ctl = navigator.serviceWorker?.controller
    if (!ctl) return resolve(null)
    const ch = new MessageChannel()
    const t = window.setTimeout(() => resolve(null), 1500)
    ch.port1.onmessage = (ev) => { window.clearTimeout(t); resolve(typeof ev.data?.version === 'string' ? ev.data.version : null) }
    ctl.postMessage({ type: 'GET_VERSION' }, [ch.port2])
  })
}

export interface StorageInfo { persisted: boolean | null; usage: number | null; quota: number | null; supported: boolean }

export async function getStorageInfo(): Promise<StorageInfo> {
  const s = navigator.storage
  if (!s) return { persisted: null, usage: null, quota: null, supported: false }
  const [persisted, est] = await Promise.all([
    s.persisted ? s.persisted().catch(() => null) : Promise.resolve(null),
    s.estimate ? s.estimate().catch(() => null) : Promise.resolve(null),
  ])
  return { persisted, usage: est?.usage ?? null, quota: est?.quota ?? null, supported: true }
}

/** Ask the browser not to evict this origin's data under storage pressure. Safe to call repeatedly. */
export async function requestPersistence(): Promise<boolean | null> {
  try {
    if (!navigator.storage?.persist) return null
    if (navigator.storage.persisted && (await navigator.storage.persisted())) return true
    return await navigator.storage.persist()
  } catch {
    return null
  }
}
