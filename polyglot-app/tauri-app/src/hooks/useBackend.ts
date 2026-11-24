const API_BASE = 'http://127.0.0.1:8125'
const GO_BASE = 'http://127.0.0.1:8126'

export async function apiHealth() {
  const res = await fetch(`${API_BASE}/health`, { signal: AbortSignal.timeout(3000) })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function goHealth() {
  try {
    const res = await fetch(`${GO_BASE}/health`, { signal: AbortSignal.timeout(3000) })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
  } catch (e: any) {
    return { status: 'offline', error: e.message }
  }
}

async function withRetry<T>(fn: () => Promise<T>, retries = 2, delay = 500): Promise<T> {
  try {
    return await fn()
  } catch (err) {
    await frontendLog(`api_retry ${String(err)} retries=${retries}`)
    if (retries <= 0) throw err
    await new Promise(resolve => setTimeout(resolve, delay))
    return withRetry(fn, retries - 1, delay * 2)
  }
}

export async function listAccounts() {
  return withRetry(() => fetch(`${API_BASE}/api/accounts`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createAccount(account: { name: string; type?: string; balance?: number; currency?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/accounts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(account),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function importTransactions(rows: Array<Record<string, any>>) {
  const res = await fetch(`${API_BASE}/api/transactions/import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ rows }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function listTransactions() {
  return withRetry(() => fetch(`${API_BASE}/api/transactions`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function searchTransactions(params: { search?: string; category_id?: string; account_id?: string; start_date?: string; end_date?: string }) {
  const qs = new URLSearchParams()
  if (params.search) qs.set('search', params.search)
  if (params.category_id) qs.set('category_id', params.category_id)
  if (params.account_id) qs.set('account_id', params.account_id)
  if (params.start_date) qs.set('start_date', params.start_date)
  if (params.end_date) qs.set('end_date', params.end_date)
  return withRetry(() => fetch(`${API_BASE}/api/transactions/search?${qs.toString()}`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function suggestTransactionCategories(description: string) {
  return fetch(`${API_BASE}/api/transactions/category-suggestions?description=${encodeURIComponent(description)}`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() })
}

export async function createTransaction(tx: { account_id: string; amount: number; description: string; category_id?: string; date: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(tx),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function updateTransaction(id: string, tx: { description?: string; amount?: number }) {
  return withRetry(() => fetch(`${API_BASE}/api/transactions/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(tx),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function deleteTransaction(id: string) {
  return withRetry(() => fetch(`${API_BASE}/api/transactions/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listCategories() {
  return withRetry(() => fetch(`${API_BASE}/api/categories`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listBudgets() {
  return withRetry(() => fetch(`${API_BASE}/api/budgets`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createBudget(budget: { category_id: string; amount: number; period?: string; start_date: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/budgets`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(budget),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function updateBudget(id: string, budget: { amount?: number; period?: string; category_id?: string; start_date?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/budgets/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(budget),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function deleteBudget(id: string) {
  return withRetry(() => fetch(`${API_BASE}/api/budgets/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createCategory(category: { name: string; color?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/categories`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(category),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function updateCategory(id: string, category: { name?: string; color?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/categories/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(category),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function deleteCategory(id: string) {
  return withRetry(() => fetch(`${API_BASE}/api/categories/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function updateAccount(id: string, account: { name?: string; type?: string; balance?: number; currency?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/accounts/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(account),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function deleteAccount(id: string) {
  return withRetry(() => fetch(`${API_BASE}/api/accounts/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getBudgetAlerts() {
  return withRetry(() => fetch(`${API_BASE}/api/budgets/alerts`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getNetWorth() {
  return withRetry(() => fetch(`${API_BASE}/api/net-worth`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getNetWorthHistory() {
  return withRetry(() => fetch(`${API_BASE}/api/net-worth/history`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getSpendingByCategory() {
  return withRetry(() => fetch(`${API_BASE}/api/spending-by-category`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listRecurringTransactions() {
  return withRetry(() => fetch(`${API_BASE}/api/recurring-transactions`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createRecurringTransaction(payload: { account_id: string; amount: number; description: string; frequency?: string; next_date: string; category_id?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/recurring-transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function updateRecurringTransaction(id: string, payload: { amount?: number; description?: string; frequency?: string; next_date?: string; category_id?: string; active?: boolean }) {
  return withRetry(() => fetch(`${API_BASE}/api/recurring-transactions/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function deleteRecurringTransaction(id: string) {
  return withRetry(() => fetch(`${API_BASE}/api/recurring-transactions/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function runRecurringScheduler() {
  const USE_TAURI = typeof window !== 'undefined' && !!(window as any).__TAURI__?.invoke
  if (!USE_TAURI) return fetch(`${API_BASE}/api/recurring-transactions/run-scheduler`, { method: 'POST', headers: { 'Content-Type': 'application/json' } }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() })
  return (window as any).__TAURI__.invoke('run_recurring_scheduler')
}

export async function frontendLog(message: string) {
  const USE_TAURI = typeof window !== 'undefined' && !!(window as any).__TAURI__?.invoke
  if (!USE_TAURI) return
  try { await (window as any).__TAURI__.invoke('frontend_log', { message }) } catch {}
}

export async function getSchedulerStatus() {
  const USE_TAURI = typeof window !== 'undefined' && !!(window as any).__TAURI__?.invoke
  if (!USE_TAURI) return { last_run_secs: null, next_run_secs: null, running: false }
  return (window as any).__TAURI__.invoke('get_scheduler_status')
}

export async function globalSearch(q: string) {
  return withRetry(() => fetch(`${API_BASE}/api/search?q=${encodeURIComponent(q)}`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getSummaryReport(year?: number, month?: number) {
  const params = new URLSearchParams()
  if (year) params.set('year', String(year))
  if (month) params.set('month', String(month))
  return withRetry(() => fetch(`${API_BASE}/api/reports/summary?${params.toString()}`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listSavingsGoals() {
  return withRetry(() => fetch(`${API_BASE}/api/savings-goals`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createSavingsGoal(goal: { name: string; target_amount: number; current_amount?: number; deadline?: string; account_id?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/savings-goals`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(goal),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function updateSavingsGoal(id: string, goal: { name?: string; target_amount?: number; current_amount?: number; deadline?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/savings-goals/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(goal),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function deleteSavingsGoal(id: string) {
  return withRetry(() => fetch(`${API_BASE}/api/savings-goals/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getSpendingForecast(months: number = 3) {
  return withRetry(() => fetch(`${API_BASE}/api/forecast/spending?months=${months}`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function undoTransaction(id: string) {
  return withRetry(() => fetch(`${API_BASE}/api/transactions/${encodeURIComponent(id)}/undo`, {
    method: 'POST',
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listDeletedTransactions() {
  return withRetry(() => fetch(`${API_BASE}/api/transactions/deleted`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

let _authToken: string | null = null

export function setAuthToken(token: string | null) {
  _authToken = token
  if (token) localStorage.setItem('ss_token', token)
  else localStorage.removeItem('ss_token')
}

export function getAuthToken(): string | null {
  if (_authToken) return _authToken
  _authToken = localStorage.getItem('ss_token')
  return _authToken
}

function authHeaders(): Record<string, string> {
  const token = getAuthToken()
  return token ? { 'Authorization': `Bearer ${token}` } : {}
}

export async function register(username: string, email: string, password: string) {
  const res = await fetch(`${API_BASE}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, email, password }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data = await res.json()
  setAuthToken(data.token)
  return data
}

export async function login(username: string, password: string) {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data = await res.json()
  setAuthToken(data.token)
  return data
}

export async function getMe() {
  return withRetry(() => fetch(`${API_BASE}/api/auth/me`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function splitTransaction(transactionId: string, splits: Array<{ category_id?: string; amount: number; description?: string }>) {
  return withRetry(() => fetch(`${API_BASE}/api/transactions/${encodeURIComponent(transactionId)}/split`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ splits }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getTransactionSplits(transactionId: string) {
  return withRetry(() => fetch(`${API_BASE}/api/transactions/${encodeURIComponent(transactionId)}/splits`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listWidgetLayouts() {
  return withRetry(() => fetch(`${API_BASE}/api/widget-layouts`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function saveWidgetLayout(layout: { widget_id: string; position_x: number; position_y: number; width: number; height: number; visible: boolean }) {
  return withRetry(() => fetch(`${API_BASE}/api/widget-layouts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(layout),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listRecurringTemplates() {
  return withRetry(() => fetch(`${API_BASE}/api/recurring-templates`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createRecurringTemplate(template: { name: string; amount: number; category_id?: string; frequency?: string; description?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/recurring-templates`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(template),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function deleteRecurringTemplate(id: string) {
  return withRetry(() => fetch(`${API_BASE}/api/recurring-templates/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: authHeaders(),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createBackup() {
  return withRetry(() => fetch(`${API_BASE}/api/backup`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listBackups() {
  return withRetry(() => fetch(`${API_BASE}/api/backups`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createAuditLog(entityType: string, entityId: string, action: string, details?: string) {
  return withRetry(() => fetch(`${API_BASE}/api/audit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ entity_type: entityType, entity_id: entityId, action, details }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listAuditLogs(limit: number = 50) {
  return withRetry(() => fetch(`${API_BASE}/api/audit?limit=${limit}`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function setup2FA() {
  return withRetry(() => fetch(`${API_BASE}/api/auth/2fa/setup`, { method: 'POST', headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function verify2FA(code: string) {
  return withRetry(() => fetch(`${API_BASE}/api/auth/2fa/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ code }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listDebts() {
  return withRetry(() => fetch(`${API_BASE}/api/debts`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createDebt(debt: { name: string; creditor?: string; balance: number; interest_rate?: number; minimum_payment?: number; due_date?: string; debt_type?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/debts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(debt),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function addDebtPayment(debtId: string, payment: { amount: number; payment_date?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/debts/${encodeURIComponent(debtId)}/payments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(payment),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listInvestments() {
  return withRetry(() => fetch(`${API_BASE}/api/investments`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createInvestment(inv: { name: string; ticker?: string; shares: number; cost_basis: number; current_price: number; investment_type?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/investments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(inv),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createBillSplit(split: { transaction_id?: string; total_amount: number; description?: string; participants: Array<{ name: string; amount_owed: number; paid?: boolean }> }) {
  return withRetry(() => fetch(`${API_BASE}/api/bill-splits`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(split),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listBillSplits() {
  return withRetry(() => fetch(`${API_BASE}/api/bill-splits`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createTaxTag(tag: { transaction_id: string; category: string; deductible?: boolean; notes?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/tax-tags`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(tag),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listTaxTags(year?: number) {
  const params = year ? `?year=${year}` : ''
  return withRetry(() => fetch(`${API_BASE}/api/tax-tags${params}`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createIncomeStream(stream: { name: string; amount: number; frequency?: string; category?: string; next_date?: string }) {
  return withRetry(() => fetch(`${API_BASE}/api/income-streams`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(stream),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listIncomeStreams() {
  return withRetry(() => fetch(`${API_BASE}/api/income-streams`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getSpendingInsights(months: number = 6) {
  return withRetry(() => fetch(`${API_BASE}/api/insights/spending?months=${months}`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listCreditScores() {
  return withRetry(() => fetch(`${API_BASE}/api/credit-scores`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function addCreditScore(score: number, provider?: string, factors?: string) {
  return withRetry(() => fetch(`${API_BASE}/api/credit-scores`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ score, provider, factors }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getTransactionTags(transactionId: string) {
  return withRetry(() => fetch(`${API_BASE}/api/transactions/${encodeURIComponent(transactionId)}/tags`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function addTransactionTags(transactionId: string, tags: string[]) {
  return withRetry(() => fetch(`${API_BASE}/api/transactions/${encodeURIComponent(transactionId)}/tags`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ tags }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getTransactionsByTag(tag: string) {
  return withRetry(() => fetch(`${API_BASE}/api/transactions/tags/${encodeURIComponent(tag)}`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listMilestones(goalId: string) {
  return withRetry(() => fetch(`${API_BASE}/api/goals/${encodeURIComponent(goalId)}/milestones`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function addMilestone(goalId: string, name: string, targetAmount: number) {
  return withRetry(() => fetch(`${API_BASE}/api/goals/${encodeURIComponent(goalId)}/milestones`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ name, target_amount: targetAmount }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listSpendingLimits() {
  return withRetry(() => fetch(`${API_BASE}/api/spending-limits`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createSpendingLimit(categoryId: string, limitAmount: number, period: string = 'monthly', alertThreshold: number = 0.8) {
  return withRetry(() => fetch(`${API_BASE}/api/spending-limits`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ category_id: categoryId, limit_amount: limitAmount, period, alert_threshold: alertThreshold }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function listBillReminders() {
  return withRetry(() => fetch(`${API_BASE}/api/bill-reminders`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function createBillReminder(recurringId: string, reminderDaysBefore: number = 3) {
  return withRetry(() => fetch(`${API_BASE}/api/bill-reminders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ recurring_id: recurringId, reminder_days_before: reminderDaysBefore }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getExchangeRates(base: string = 'USD') {
  return withRetry(() => fetch(`${API_BASE}/api/exchange-rates?base=${base}`).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function exportQuickBooks() {
  return withRetry(() => fetch(`${API_BASE}/api/export/quickbooks`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function exportCSV() {
  return withRetry(() => fetch(`${API_BASE}/api/export/csv`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function sendReportEmail(email: string, month?: number, year?: number) {
  return withRetry(() => fetch(`${API_BASE}/api/reports/send-email`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ email, month, year }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getNetIncomeReport(months: number = 12) {
  return withRetry(() => fetch(`${API_BASE}/api/reports/net-income?months=${months}`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getCategoryHierarchy() {
  return withRetry(() => fetch(`${API_BASE}/api/categories/hierarchy`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getDebtPayoffStrategy(strategy: string = 'snowball', months: number = 60) {
  return withRetry(() => fetch(`${API_BASE}/api/debts/strategy?strategy=${strategy}&months=${months}`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function getInvestmentPerformance(months: number = 12) {
  return withRetry(() => fetch(`${API_BASE}/api/investments/performance?months=${months}`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

// ---- Data connectors (CSV / OFX / Open Banking / manual) ----

export async function listConnectors() {
  return withRetry(() => fetch(`${API_BASE}/api/connectors`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function connectConnector(name: string, credentials: Record<string, any> = {}) {
  return withRetry(() => fetch(`${API_BASE}/api/connectors/${name}/connect`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ credentials }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function fetchConnector(name: string, params: Record<string, any> = {}, account_id?: string, dry_run: boolean = false) {
  return withRetry(() => fetch(`${API_BASE}/api/connectors/${name}/fetch`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ params, account_id, dry_run }),
  }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

export async function previewCsvImport(csv: string, account_id?: string) {
  // Parse-only: returns normalized records without persisting (dry_run).
  return fetchConnector('csv', { content: csv }, account_id, true)
}

export async function getHostInfo() {
  try {
    const res = await fetch(`${API_BASE}/api/host/info`, { signal: AbortSignal.timeout(3000) })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
  } catch (e: any) {
    return { error: e.message }
  }
}

export async function getBudgetComparison() {
  return withRetry(() => fetch(`${API_BASE}/api/budgets/comparison`, { headers: authHeaders() }).then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }))
}

// ---- Optional phone sync hub (docs/sync.md). Hub-machine only: the pairing key and device list
// are never returned to remote callers, so these work from this desktop app and nowhere else.
export interface HubInfo { name: string; lan_ip: string; port: number; key: string | null }
export interface SyncDevice { id: string; name: string; created_at?: string; last_seen?: string | null; revoked?: boolean }

async function hubJson(path: string, init?: RequestInit) {
  const headers = init?.body ? { 'Content-Type': 'application/json', ...(init.headers || {}) } : init?.headers
  const res = await fetch(`${API_BASE}${path}`, { ...init, headers })
  if (!res.ok) {
    const detail = await res.json().then((j: any) => j?.detail).catch(() => null)
    throw new Error(typeof detail === 'string' ? detail : `HTTP ${res.status}`)
  }
  return res.json()
}

export const getHubInfo = (): Promise<HubInfo> => hubJson('/api/host/info')
export const rotateHubKey = (): Promise<{ key: string }> => hubJson('/api/host/rotate-key', { method: 'POST' })
export const listSyncDevices = async (): Promise<SyncDevice[]> => {
  const data = await hubJson('/api/sync/v1/devices')
  return Array.isArray(data) ? data : (data.devices ?? [])
}

export interface LinkStatus {
  available: boolean; enabled: boolean; running: boolean; relays: string[]; name: string
  node_id: string | null; error: string | null; peers: { node_id: string; name: string }[]
}
export const getLinkStatus = (): Promise<LinkStatus> => hubJson('/api/link/status')
export const enableLink = (relays: string[], name?: string): Promise<LinkStatus> =>
  hubJson('/api/link/enable', { method: 'POST', body: JSON.stringify({ relays, name }) })
export const disableLink = (): Promise<LinkStatus> => hubJson('/api/link/disable', { method: 'POST' })
export const createLinkInvite = (): Promise<{ code: string; invite: string; node_id: string }> =>
  hubJson('/api/link/invite', { method: 'POST', body: JSON.stringify({ ttl_s: 600 }) })

export const revokeSyncDevice = (id: string) =>
  hubJson(`/api/sync/v1/devices/${encodeURIComponent(id)}`, { method: 'DELETE' })

// ---- SimpleFIN (optional US bank link; see docs/bank-linking.md) ----
export interface SimpleFinStatus {
  connected: boolean
  last_sync: string | null
  last_result: { accounts: number; added: number; skipped: number } | null
  last_error: string | null
  min_interval_s: number
}
export const getSimpleFinStatus = (): Promise<SimpleFinStatus> => hubJson('/api/simplefin/status')
export const connectSimpleFin = (setup_token: string): Promise<SimpleFinStatus> =>
  hubJson('/api/simplefin/connect', { method: 'POST', body: JSON.stringify({ setup_token }) })
export const syncSimpleFin = (): Promise<SimpleFinStatus & { result: SimpleFinStatus['last_result']; messages: string[] }> =>
  hubJson('/api/simplefin/sync', { method: 'POST' })
export const disconnectSimpleFin = (): Promise<SimpleFinStatus & { note: string }> =>
  hubJson('/api/simplefin', { method: 'DELETE' })
