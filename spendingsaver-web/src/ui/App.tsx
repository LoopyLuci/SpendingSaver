import { Suspense, lazy, useEffect, useRef, useState, type ReactNode } from 'react'
import { applyTheme } from './theme'
import { navigate, useRoute } from './router'
import { useSnapshot, useStore, useToast } from './context'
import { Icon } from './Icon'
import { postDueRecurring } from '../storage/actions'
import { applyUpdate, dismissCached, isStandalone, promptInstall, requestPersistence, usePwa } from '../pwa/pwa'
import { useHasUnsavedEdits } from './dirty'
import Dashboard from './pages/Dashboard'
import Transactions from './pages/Transactions'

const Accounts = lazy(() => import('./pages/Accounts'))
const Categories = lazy(() => import('./pages/Categories'))
const Budgets = lazy(() => import('./pages/Budgets'))
const Recurring = lazy(() => import('./pages/Recurring'))
const Goals = lazy(() => import('./pages/Goals'))
const Debts = lazy(() => import('./pages/Debts'))
const Assistant = lazy(() => import('./pages/Assistant'))
const Tax = lazy(() => import('./pages/Tax'))
const DataPage = lazy(() => import('./pages/DataPage'))
const SettingsPage = lazy(() => import('./pages/Settings'))

interface NavItem { path: string; label: string; icon: string; title: string }

const NAV: NavItem[] = [
  { path: '/', label: 'Dashboard', icon: 'home', title: 'Dashboard' },
  { path: '/transactions', label: 'Transactions', icon: 'list', title: 'Transactions' },
  { path: '/accounts', label: 'Accounts', icon: 'wallet', title: 'Accounts' },
  { path: '/categories', label: 'Categories', icon: 'tag', title: 'Categories' },
  { path: '/budgets', label: 'Budgets', icon: 'target', title: 'Budgets' },
  { path: '/recurring', label: 'Recurring', icon: 'repeat', title: 'Recurring' },
  { path: '/goals', label: 'Goals', icon: 'flag', title: 'Savings goals' },
  { path: '/debts', label: 'Debts', icon: 'chart', title: 'Debt payoff' },
  { path: '/assistant', label: 'Assistant', icon: 'chat', title: 'Assistant' },
  { path: '/tax', label: 'Tax estimate', icon: 'calc', title: 'Tax estimate' },
  { path: '/data', label: 'Import / Export', icon: 'file', title: 'Import, export and backup' },
  { path: '/settings', label: 'Settings', icon: 'cog', title: 'Settings' },
]
const TABS: NavItem[] = [
  NAV[0], NAV[1], NAV[4], NAV[8],
  { path: '/more', label: 'More', icon: 'more', title: 'More' },
]

function active(item: NavItem, path: string): boolean {
  if (item.path === '/more') return !TABS.slice(0, 4).some((t) => t.path === path)
  return path === item.path
}

function MorePage() {
  return (
    <div>
      <header className="page-head"><h1 tabIndex={-1} id="page-title">More</h1></header>
      <ul className="card list nav-list">
        {NAV.filter((n) => !TABS.slice(0, 4).includes(n)).filter((n) => n.path !== '/').map((n) => (
          <li key={n.path}>
            <a className="list-row" href={`#${n.path}`}><Icon name={n.icon} /><span className="grow">{n.title}</span></a>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Banner({ tone = 'info', children, role = 'status' }: { tone?: 'info' | 'warn'; children: ReactNode; role?: 'status' | 'alert' }) {
  return <div className={`banner ${tone}`} role={role}>{children}</div>
}

function useSessionFlag(key: string): [boolean, () => void] {
  const [v, setV] = useState(() => { try { return window.localStorage.getItem(key) === '1' } catch { return false } })
  return [v, () => { setV(true); try { window.localStorage.setItem(key, '1') } catch { /* ignore */ } }]
}

function Banners() {
  const { settings, live, storageKind, writeError } = useSnapshot()
  const pwa = usePwa()
  const unsaved = useHasUnsavedEdits()
  const [hideBackup, setHideBackup] = useState(false)
  const [hideInstall, dismissInstall] = useSessionFlag('ss-install-dismissed')
  const toast = useToast()
  const day = 24 * 3600 * 1000
  const route = useRoute()
  const backupStale = route.path === '/' && live.transactions.length >= 5 && Date.now() - settings.lastBackupAt > 30 * day && !hideBackup

  return (
    <div className="banners">
      {(storageKind === 'memory' || writeError) && (
        <Banner tone="warn" role="alert">
          <strong>Your data is not being saved.</strong> {writeError ?? 'Browser storage is unavailable.'} Everything you enter now disappears when you close this tab. Export a backup from Import / Export before leaving, or allow site data for this site and reload.
        </Banner>
      )}
      {pwa.updateReady && (
        <Banner>
          <span><strong>Update available.</strong> {unsaved ? 'Save or close the form you are editing, then reload.' : 'Reload to use the new version.'}</span>
          <button type="button" className="btn small primary" onClick={applyUpdate} disabled={unsaved}>Reload</button>
        </Banner>
      )}
      {pwa.justCached && (
        <Banner>
          <span>SpendingSaver is saved on this device and now works offline.</span>
          <button type="button" className="btn small" onClick={dismissCached}>OK</button>
        </Banner>
      )}
      {!pwa.installed && !isStandalone() && !hideInstall && (pwa.canInstall || pwa.ios) && (
        <Banner>
          {pwa.canInstall ? (
            <>
              <span>Install SpendingSaver as an app on this device.</span>
              <span className="row gap">
                <button type="button" className="btn small primary" onClick={() => { void promptInstall().then((r) => { if (r === 'accepted') toast.show('Installed') }) }}>Install</button>
                <button type="button" className="btn small" onClick={dismissInstall}>Not now</button>
              </span>
            </>
          ) : (
            <>
              <span>To install on iPhone or iPad: tap the Share button, then <strong>Add to Home Screen</strong>.</span>
              <button type="button" className="btn small" onClick={dismissInstall}>Got it</button>
            </>
          )}
        </Banner>
      )}
      {backupStale && (
        <Banner>
          <span>{settings.lastBackupAt ? 'Your last backup is over a month old.' : 'You have not made a backup yet.'} Data lives only in this browser; clearing site data deletes it.</span>
          <span className="row gap">
            <a className="btn small primary" href="#/data">Back up</a>
            <button type="button" className="btn small" onClick={() => setHideBackup(true)}>Later</button>
          </span>
        </Banner>
      )}
      <ToastHost />
    </div>
  )
}

function ToastHost() {
  const { toasts, dismiss } = useToast()
  return (
    <div className="toasts" role="status" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className={t.tone === 'error' ? 'toast err' : 'toast'}>
          <span>{t.message}</span>
          {t.action && <button type="button" className="btn small" onClick={() => { t.action!.run(); dismiss(t.id) }}>{t.action.label}</button>}
          <button type="button" className="btn small ghost icon-btn" onClick={() => dismiss(t.id)} aria-label="Dismiss"><Icon name="close" size={16} /></button>
        </div>
      ))}
    </div>
  )
}

export default function App() {
  const route = useRoute()
  const store = useStore()
  const { settings, live } = useSnapshot()
  const main = useRef<HTMLElement>(null)
  const first = useRef(true)

  useEffect(() => { applyTheme(settings.theme, settings.accent) }, [settings.theme, settings.accent])
  useEffect(() => {
    if (settings.theme !== 'system') return
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const on = () => applyTheme('system', settings.accent)
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [settings.theme, settings.accent])

  // Post recurring items that are due, on load and whenever the app comes back to the foreground.
  const toast = useToast()
  useEffect(() => {
    const run = () => { postDueRecurring(store).then((n) => { if (n > 0) toast.show(`Posted ${n} recurring transaction${n === 1 ? '' : 's'}.`) }).catch(() => undefined) }
    run()
    const vis = () => { if (document.visibilityState === 'visible') run() }
    document.addEventListener('visibilitychange', vis)
    return () => document.removeEventListener('visibilitychange', vis)
  }, [store, toast])

  // Ask for persistent storage once the user actually has data worth protecting.
  const asked = useRef(false)
  useEffect(() => {
    if (!asked.current && (live.accounts.length > 0 || live.transactions.length > 0)) { asked.current = true; void requestPersistence() }
  }, [live.accounts.length, live.transactions.length])

  const item = NAV.find((n) => n.path === route.path)
  const title = route.path === '/more' ? 'More' : item?.title ?? 'Not found'
  useEffect(() => {
    document.title = route.path === '/' ? 'SpendingSaver' : `${title} - SpendingSaver`
    if (first.current) { first.current = false; return }
    document.getElementById('page-title')?.focus({ preventScroll: true })
    window.scrollTo(0, 0)
  }, [route.path, title])

  let page: ReactNode
  switch (route.path) {
    case '/': page = <Dashboard />; break
    case '/transactions': page = <Transactions />; break
    case '/accounts': page = <Accounts />; break
    case '/categories': page = <Categories />; break
    case '/budgets': page = <Budgets />; break
    case '/recurring': page = <Recurring />; break
    case '/goals': page = <Goals />; break
    case '/debts': page = <Debts />; break
    case '/assistant': page = <Assistant />; break
    case '/tax': page = <Tax />; break
    case '/data': page = <DataPage />; break
    case '/settings': page = <SettingsPage />; break
    case '/more': page = <MorePage />; break
    default:
      page = (
        <div className="empty"><h1 tabIndex={-1} id="page-title">Page not found</h1><p>That address does not exist.</p><a className="btn primary" href="#/">Go to the dashboard</a></div>
      )
  }

  return (
    <div className="app">
      <a className="skip" href="#main" onClick={(e) => { e.preventDefault(); main.current?.focus() }}>Skip to content</a>
      <aside className="sidebar" aria-label="Main">
        <a className="brand" href="#/"><img src="./icons/icon-192.png" alt="" width="32" height="32" /> <span>SpendingSaver</span></a>
        <nav aria-label="Primary">
          <ul>
            {NAV.map((n) => (
              <li key={n.path}>
                <a href={`#${n.path}`} className={active(n, route.path) || (n.path === route.path) ? 'nav-link on' : 'nav-link'} aria-current={n.path === route.path ? 'page' : undefined}>
                  <Icon name={n.icon} /> <span>{n.label}</span>
                </a>
              </li>
            ))}
          </ul>
        </nav>
        <p className="side-note">Stored only in this browser.</p>
      </aside>
      <div className="content">
        <header className="topbar">
          <a className="brand" href="#/"><img src="./icons/icon-192.png" alt="" width="28" height="28" /> <span>SpendingSaver</span></a>
          <button type="button" className="btn primary small" onClick={() => navigate('/transactions?add=1')}><Icon name="plus" size={18} /> Add</button>
        </header>
        <main id="main" ref={main} tabIndex={-1}>
          <Banners />
          <Suspense fallback={<div className="loading" role="status">Loading...</div>}>{page}</Suspense>
        </main>
      </div>
      <nav className="tabbar" aria-label="Primary">
        {TABS.map((t) => (
          <a key={t.path} href={`#${t.path}`} className={active(t, route.path) ? 'tab on' : 'tab'} aria-current={t.path === route.path ? 'page' : undefined}>
            <Icon name={t.icon} /><span>{t.label}</span>
          </a>
        ))}
      </nav>
    </div>
  )
}
