import { Routes, Route, Link } from 'react-router-dom'
import Dashboard from './pages/Dashboard'
import Transactions from './pages/Transactions'
import Budgets from './pages/Budgets'
import Accounts from './pages/Accounts'
import NetWorth from './pages/NetWorth'
import ImportExport from './pages/ImportExport'
import { ThemeProvider, useTheme } from './hooks/useTheme'
import { seedCategories } from './db/db'
import { useEffect } from 'react'

function NavigationBar() {
  const { theme, toggleTheme } = useTheme()
  return (
    <nav className="bg-gray-100 dark:bg-gray-800 p-4 flex gap-4 items-center shadow">
      <Link to="/" className="font-bold text-lg text-blue-600 dark:text-blue-400">SpendingSaver</Link>
      <Link to="/" className="hover:underline">Dashboard</Link>
      <Link to="/transactions" className="hover:underline">Transactions</Link>
      <Link to="/budgets" className="hover:underline">Budgets</Link>
      <Link to="/accounts" className="hover:underline">Accounts</Link>
      <Link to="/networth" className="hover:underline">Net Worth</Link>
      <Link to="/import" className="hover:underline">Import/Export</Link>
      <button
        onClick={toggleTheme}
        className="ml-auto px-3 py-1 rounded border dark:border-gray-600 hover:bg-gray-200 dark:hover:bg-gray-700"
      >
        {theme === 'light' ? '🌙 Dark' : '☀️ Light'}
      </button>
    </nav>
  )
}

export default function App() {
  useEffect(() => {
    seedCategories()
  }, [])

  return (
    <ThemeProvider>
      <div className="min-h-screen bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100">
        <NavigationBar />
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/transactions" element={<Transactions />} />
          <Route path="/budgets" element={<Budgets />} />
          <Route path="/accounts" element={<Accounts />} />
          <Route path="/networth" element={<NetWorth />} />
          <Route path="/import" element={<ImportExport />} />
        </Routes>
      </div>
    </ThemeProvider>
  )
}
