import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import Dashboard from '../src/pages/Dashboard'
import Transactions from '../src/pages/Transactions'
import Accounts from '../src/pages/Accounts'
import Budgets from '../src/pages/Budgets'
import Diagnostics from '../src/pages/Diagnostics'
import NetWorth from '../src/pages/NetWorth'
import ImportExport from '../src/pages/ImportExport'
import Settings from '../src/pages/Settings'
import Recurring from '../src/pages/Recurring'

vi.mock('../src/hooks/useBackend', async () => {
  const actual = await vi.importActual<any>('../src/hooks/useBackend')
  return {
    ...actual,
    getNetWorth: async () => ({ net_worth: 1234.56 }),
    getSpendingByCategory: async () => [{ name: 'Food', value: 10 }],
    getBudgetAlerts: async () => [],
    getNetWorthHistory: async () => [{ date: '2026-01-01', net_worth: 1000 }, { date: '2026-01-02', net_worth: 1100 }],
    listAccounts: async () => [{ id: '1', name: 'Checking', balance: 100, type: 'checking', currency: 'USD' }],
    listTransactions: async () => [],
    listBudgets: async () => [{ id: '1', category_id: 'c1', amount: 500, period: 'monthly', start_date: '2026-01-01' }],
    listCategories: async () => [{ id: 'c1', name: 'Food' }],
    suggestTransactionCategories: async (desc: string) => desc.includes('starbucks') ? [{ category_id: 'c1', description: 'Starbucks' }] : [],
    listRecurringTransactions: async () => [],
    createAccount: async () => ({ id: '2', name: 'Savings', balance: 0, type: 'savings', currency: 'USD' }),
    createTransaction: async () => ({ id: '3', description: 'Test', amount: -5 }),
    createBudget: async () => ({ id: '4', category_id: 'c1', amount: 200, period: 'monthly', start_date: '2026-01-01' }),
    updateTransaction: async () => ({ id: '3', description: 'Test', amount: -5 }),
    deleteTransaction: async () => ({ deleted: '3' }),
    runRecurringScheduler: async () => ({ created: 0 }),
    getSchedulerStatus: async () => ({ last_run_secs: 0, next_run_secs: 21600, running: false }),
  }
})

describe('SpendingSaver pages', () => {
  it('renders dashboard elements', async () => {
    render(<Dashboard />)
    expect(screen.getAllByText(/Net Worth/i).length).toBeGreaterThan(0)
    expect(await screen.findByText(/1234\.56/)).toBeTruthy()
    expect(screen.getByText(/Net Worth Over Time/i)).toBeTruthy()
  })

  it('renders transactions elements', async () => {
    render(<Transactions />)
    expect(screen.getByText(/Add Transaction/i)).toBeTruthy()
    expect(screen.getByText(/Transactions/i)).toBeTruthy()
  })

  it('shows category suggestions for a known description', async () => {
    render(<Transactions />)
    const input = await screen.findByPlaceholderText(/Description/i)
    const nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')!.set!
    nativeInputValueSetter.call(input, 'starbucks')
    input.dispatchEvent(new Event('input', { bubbles: true }))
    expect(await screen.findByText(/Starbucks/i, {}, { timeout: 2000 })).toBeTruthy()
  })

  it('renders accounts elements', () => {
    render(<Accounts />)
    expect(screen.getByText(/Add Account/i)).toBeTruthy()
    expect(screen.getByText(/Accounts/i)).toBeTruthy()
  })

  it('renders budgets elements', () => {
    render(<Budgets />)
    expect(screen.getByText(/Add Budget/i)).toBeTruthy()
    expect(screen.getByText(/Budgets/i)).toBeTruthy()
  })

  it('renders diagnostics elements', () => {
    render(<Diagnostics />)
    expect(screen.getByText(/Diagnostics/i)).toBeTruthy()
  })

  it('renders net worth elements', async () => {
    render(<NetWorth />)
    expect(screen.getByText(/Net Worth/i)).toBeTruthy()
    expect(await screen.findByText(/\$1234\.56/)).toBeTruthy()
  })

  it('renders import/export elements', () => {
    render(<ImportExport />)
    expect(screen.getByText(/Import CSV/i)).toBeTruthy()
    expect(screen.getByText(/Export transactions as CSV/i)).toBeTruthy()
  })

  it('renders settings elements', () => {
    render(<Settings />)
    expect(screen.getByText(/Appearance/i)).toBeTruthy()
    expect(screen.getByText(/Currency & dates/i)).toBeTruthy()
  })

  it('renders recurring page elements', async () => {
    render(<Recurring />)
    expect(screen.getByText(/Recurring Transactions/i)).toBeTruthy()
    expect(screen.getAllByText(/Auto-runs every 6 hours/i).length).toBeGreaterThanOrEqual(1)
  })
})
