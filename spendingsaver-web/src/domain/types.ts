import type { Period } from '../lib/dates'

/**
 * Every synced-capable record carries `id` (UUID string), `updatedAt` (epoch ms) and `deleted` (soft-delete
 * tombstone), the same trio the Android app, the desktop app and `spendingsaver-desktop/docs/sync.md` use.
 * Money is integer cents (`*Cents`); the JSON backup converts to/from decimal numbers.
 */
export interface Row {
  id: string
  updatedAt: number
  deleted: boolean
}

export interface Account extends Row {
  name: string
  /** Free text; suggestions come from ACCOUNT_TYPES. */
  type: string
  /** Balance as entered by the user. Transactions do not change it (same as the Android and desktop apps). */
  balanceCents: number
  currency: string
}

export interface Category extends Row {
  name: string
  parentId: string | null
  /** "#rrggbb" or null. */
  color: string | null
}

export interface Transaction extends Row {
  accountId: string
  /** Positive is income, negative is an expense. */
  amountCents: number
  description: string
  categoryId: string | null
  /** YYYY-MM-DD */
  date: string
  notes: string | null
  createdAt: number
}

export interface Budget extends Row {
  categoryId: string
  amountCents: number
  period: Period
  startDate: string
}

export type Frequency = 'weekly' | 'biweekly' | 'monthly' | 'quarterly' | 'yearly'

/** Recurring items, goals and debts live only in this browser (not in the shared backup format). */
export interface Recurring extends Row {
  name: string
  /** Negative is a bill, positive is income. */
  amountCents: number
  accountId: string
  categoryId: string | null
  frequency: Frequency
  nextDate: string
  active: boolean
}

export interface Goal extends Row {
  name: string
  targetCents: number
  savedCents: number
  due: string | null
}

export interface Debt extends Row {
  name: string
  balanceCents: number
  /** Annual percentage rate, e.g. 19.99 */
  apr: number
  minPaymentCents: number
}

export interface Dataset {
  accounts: Account[]
  categories: Category[]
  transactions: Transaction[]
  budgets: Budget[]
  recurring: Recurring[]
  goals: Goal[]
  debts: Debt[]
}

export type TableName = keyof Dataset

export const TABLES: TableName[] = ['accounts', 'categories', 'transactions', 'budgets', 'recurring', 'goals', 'debts']

export const emptyDataset = (): Dataset => ({
  accounts: [], categories: [], transactions: [], budgets: [], recurring: [], goals: [], debts: [],
})

export interface Settings {
  theme: 'system' | 'light' | 'dark'
  accent: string
  currency: string
  /** Epoch ms of the last JSON backup export, 0 if never. */
  lastBackupAt: number
  seeded: boolean
  taxProfile?: unknown
}

export const defaultSettings = (): Settings => ({
  theme: 'system', accent: 'teal', currency: 'USD', lastBackupAt: 0, seeded: false,
})

export const ACCOUNT_TYPES = ['Checking', 'Savings', 'Credit card', 'Cash', 'Investment', 'Retirement', 'Loan', 'Mortgage', 'Other']

export const CURRENCIES = [
  'USD', 'EUR', 'GBP', 'CAD', 'AUD', 'NZD', 'JPY', 'CNY', 'INR', 'CHF', 'SEK', 'NOK', 'DKK', 'PLN', 'CZK', 'HUF',
  'BRL', 'MXN', 'ARS', 'CLP', 'COP', 'ZAR', 'NGN', 'KES', 'EGP', 'AED', 'SAR', 'ILS', 'TRY', 'UAH',
  'KRW', 'SGD', 'HKD', 'TWD', 'THB', 'IDR', 'MYR', 'PHP', 'VND', 'PKR', 'BDT',
]

export const CATEGORY_IDEAS = [
  'Food', 'Groceries', 'Dining out', 'Housing', 'Utilities', 'Transportation', 'Fuel', 'Healthcare', 'Insurance',
  'Entertainment', 'Subscriptions', 'Shopping', 'Clothing', 'Travel', 'Education', 'Gifts', 'Charity', 'Pets',
  'Personal care', 'Fees', 'Taxes', 'Savings', 'Income',
]

export const COLORS: { name: string; value: string }[] = [
  { name: 'Teal', value: '#009688' }, { name: 'Blue', value: '#2196f3' }, { name: 'Green', value: '#4caf50' },
  { name: 'Purple', value: '#9c27b0' }, { name: 'Orange', value: '#ff9800' }, { name: 'Red', value: '#f44336' },
  { name: 'Pink', value: '#e91e63' }, { name: 'Yellow', value: '#ffc107' }, { name: 'Brown', value: '#795548' },
  { name: 'Grey', value: '#9e9e9e' },
]

export const DEFAULT_CATEGORIES: { name: string; color: string }[] = [
  { name: 'Income', color: '#4caf50' }, { name: 'Housing', color: '#2196f3' }, { name: 'Food', color: '#ff9800' },
  { name: 'Transportation', color: '#9c27b0' }, { name: 'Entertainment', color: '#e91e63' },
  { name: 'Utilities', color: '#009688' }, { name: 'Healthcare', color: '#f44336' }, { name: 'Other', color: '#9e9e9e' },
]

export const MAX_NAME = 120
export const MAX_DESCRIPTION = 200
export const MAX_NOTES = 2000
