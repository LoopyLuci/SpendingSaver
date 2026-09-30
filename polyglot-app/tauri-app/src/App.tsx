import { lazy, Suspense, useState, useEffect } from 'react'
import { BrowserRouter as Router, Routes, Route, NavLink, useLocation } from 'react-router-dom'

const Dashboard = lazy(() => import('./pages/Dashboard'))
const NetWorth = lazy(() => import('./pages/NetWorth'))
const Transactions = lazy(() => import('./pages/Transactions'))
const Budgets = lazy(() => import('./pages/Budgets'))
const Accounts = lazy(() => import('./pages/Accounts'))
const Categories = lazy(() => import('./pages/Categories'))
const Recurring = lazy(() => import('./pages/Recurring'))
const ImportExport = lazy(() => import('./pages/ImportExport'))
const Settings = lazy(() => import('./pages/Settings'))
const Debts = lazy(() => import('./pages/Debts'))
const Investments = lazy(() => import('./pages/Investments'))
const BillSplits = lazy(() => import('./pages/BillSplits'))
const CreditScore = lazy(() => import('./pages/CreditScore'))
const Insights = lazy(() => import('./pages/Insights'))
const AndroidDevices = lazy(() => import('./pages/AndroidDevices'))
const ConnectAccounts = lazy(() => import('./pages/ConnectAccounts'))
const Diagnostics = lazy(() => import('./pages/Diagnostics'))

const ICONS: Record<string, string> = {
  '/': '📊',
  '/net-worth': '💰',
  '/transactions': '💳',
  '/budgets': '📉',
  '/accounts': '🏦',
  '/categories': '🏷',
  '/recurring': '🔄',
  '/debts': '📋',
  '/investments': '📈',
  '/bill-splits': '👥',
  '/credit': '🏅',
  '/insights': '🔍',
  '/devices': '📱',
  '/connect': '🔗',
  '/diagnostics': '🩺',
  '/import-export': '📤',
  '/settings': '⚙',
}

const LABELS: Record<string, string> = {
  '/': 'Dashboard',
  '/net-worth': 'Net Worth',
  '/transactions': 'Transactions',
  '/budgets': 'Budgets',
  '/accounts': 'Accounts',
  '/categories': 'Categories',
  '/recurring': 'Recurring',
  '/debts': 'Debts',
  '/investments': 'Investments',
  '/bill-splits': 'Bill Splits',
  '/credit': 'Credit',
  '/insights': 'Insights',
  '/devices': 'Devices',
  '/connect': 'Connect & Import',
  '/diagnostics': 'Diagnostics',
  '/import-export': 'Import / Export',
  '/settings': 'Settings',
}

function Sidebar() {
  return (
    <aside className="w-56 bg-gray-900 text-gray-300 flex flex-col h-screen fixed left-0 top-0 z-10">
      <div className="px-4 py-5 border-b border-gray-800">
        <h1 className="text-lg font-bold text-white flex items-center gap-2">
          <span className="text-2xl">💰</span> SpendingSaver
        </h1>
      </div>
      <nav className="flex-1 overflow-y-auto py-2">
        {Object.entries(ICONS).map(([href, icon]) => (
          <NavLink
            key={href}
            to={href}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-2.5 text-sm transition-colors ${
                isActive
                  ? 'bg-blue-600/20 text-blue-400 border-r-2 border-blue-500'
                  : 'hover:bg-gray-800 hover:text-white'
              }`
            }
            end={href === '/'}
          >
            <span className="text-base">{icon}</span>
            <span>{LABELS[href]}</span>
          </NavLink>
        ))}
      </nav>
      <div className="px-4 py-3 border-t border-gray-800 text-xs text-gray-500">
        v0.1.0 • MCP Enabled
      </div>
    </aside>
  )
}

function StatusBar() {
  const [allOnline, setAllOnline] = useState(false)

  useEffect(() => {
    const poll = async () => {
      try {
        const r = await fetch('http://127.0.0.1:8125/health', { signal: AbortSignal.timeout(2000) })
        setAllOnline(r.ok)
      } catch { setAllOnline(false) }
    }
    poll()
    const interval = setInterval(poll, 10000)
    return () => clearInterval(interval)
  }, [])

  return (
    <div className="flex items-center gap-2 text-xs text-gray-400">
      <span className={`inline-flex items-center gap-1.5 ${allOnline ? 'text-emerald-400' : 'text-gray-500'}`}>
        <span className={`w-2 h-2 rounded-full inline-block ${allOnline ? 'bg-emerald-400' : 'bg-gray-600'}`} />
        {allOnline ? 'Systems online' : 'Starting services…'}
      </span>
    </div>
  )
}

function Loading() {
  return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
    </div>
  )
}

function AppLayout() {
  const location = useLocation()

  return (
    <div className="flex bg-gray-950 text-gray-100 min-h-screen">
      <Sidebar />
      <div className="flex-1 ml-56">
        <header className="bg-gray-900/80 backdrop-blur border-b border-gray-800 px-6 py-3 sticky top-0 z-10">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-medium text-gray-400">
              {LABELS[location.pathname] || 'SpendingSaver'}
            </h2>
            <StatusBar />
          </div>
        </header>
        <main className="p-6">
          <Suspense fallback={<Loading />}>
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/net-worth" element={<NetWorth />} />
              <Route path="/transactions" element={<Transactions />} />
              <Route path="/budgets" element={<Budgets />} />
              <Route path="/accounts" element={<Accounts />} />
              <Route path="/categories" element={<Categories />} />
              <Route path="/recurring" element={<Recurring />} />
              <Route path="/debts" element={<Debts />} />
              <Route path="/investments" element={<Investments />} />
              <Route path="/bill-splits" element={<BillSplits />} />
              <Route path="/credit" element={<CreditScore />} />
              <Route path="/insights" element={<Insights />} />
              <Route path="/devices" element={<AndroidDevices />} />
              <Route path="/connect" element={<ConnectAccounts />} />
              <Route path="/diagnostics" element={<Diagnostics />} />
              <Route path="/import-export" element={<ImportExport />} />
              <Route path="/settings" element={<Settings />} />
            </Routes>
          </Suspense>
        </main>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <Router>
      <AppLayout />
    </Router>
  )
}
