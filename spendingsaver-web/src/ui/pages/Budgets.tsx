import { useState, type FormEvent } from 'react'
import { useBaseCurrency, useSnapshot, useStore, useToast, useToday } from '../context'
import { centsToPlain, formatMoney, parseAmount } from '../../lib/money'
import { formatDate } from '../../lib/dates'
import { newId } from '../../lib/id'
import type { Budget } from '../../domain/types'
import { budgetStatus, inCurrency } from '../../domain/stats'
import { EmptyState, Modal, PageHeader, ProgressBar, SelectField, TextField } from '../components'
import { ensureCategory } from '../../storage/actions'
import { ComboField } from '../components'
import { useAddParam } from '../useAddParam'
import { Icon } from '../Icon'

function BudgetForm({ existing, onClose }: { existing?: Budget; onClose: () => void }) {
  const store = useStore()
  const toast = useToast()
  const { live } = useSnapshot()
  const catName = (id: string) => live.categories.find((c) => c.id === id)?.name ?? ''
  const initial = { category: existing ? catName(existing.categoryId) : '', amount: existing ? centsToPlain(existing.amountCents) : '', period: existing?.period ?? 'monthly' }
  const [f, setF] = useState(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const dirty = JSON.stringify(f) !== JSON.stringify(initial)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!f.category.trim()) errs.category = 'Choose or type a category.'
    const cents = parseAmount(f.amount)
    if (cents === null || cents <= 0) errs.amount = 'Enter a limit greater than zero, like 250.00.'
    const cid = f.category.trim() ? live.categories.find((c) => c.name.trim().toLowerCase() === f.category.trim().toLowerCase())?.id : undefined
    if (cid && live.budgets.some((b) => b.id !== existing?.id && b.categoryId === cid && b.period === f.period)) errs.category = `There is already a ${f.period} budget for this category.`
    setErrors(errs)
    if (Object.keys(errs).length) { requestAnimationFrame(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()); return }
    const categoryId = (await ensureCategory(store, f.category))!
    await store.upsert('budgets', { id: existing?.id ?? newId(), updatedAt: 0, deleted: false, categoryId, amountCents: cents!, period: f.period as Budget['period'], startDate: existing?.startDate ?? '' })
    toast.show(existing ? 'Budget updated.' : 'Budget added.')
    onClose()
  }

  return (
    <Modal title={existing ? 'Edit budget' : 'Add budget'} onClose={onClose} dirty={dirty}>
      <form onSubmit={submit} noValidate className="stack">
        <ComboField id="bu-cat" label="Category" value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })} options={live.categories.map((c) => c.name)} error={errors.category} autoFocus hint="Pick one or type a new category name." />
        <div className="row gap wrap fields-2">
          <TextField id="bu-amt" label="Limit" inputMode="decimal" value={f.amount} onChange={(e) => setF({ ...f, amount: e.target.value })} error={errors.amount} placeholder="0.00" />
          <SelectField id="bu-per" label="Period" value={f.period} onChange={(v) => setF({ ...f, period: v as Budget['period'] })}><option value="weekly">Weekly (Mon-Sun)</option><option value="monthly">Monthly</option><option value="yearly">Yearly</option></SelectField>
        </div>
        <div className="row between gap wrap">
          {existing ? <button type="button" className="btn danger-ghost" onClick={async () => { await store.softDelete('budgets', existing.id); toast.show('Budget deleted.', { action: { label: 'Undo', run: () => { void store.restoreRows('budgets', existing.id) } } }); onClose() }}><Icon name="trash" size={18} /> Delete</button> : <span />}
          <span className="row gap"><button type="button" className="btn" onClick={onClose}>Cancel</button><button type="submit" className="btn primary">{existing ? 'Save changes' : 'Add budget'}</button></span>
        </div>
      </form>
    </Modal>
  )
}

export default function Budgets() {
  const { live } = useSnapshot()
  const today = useToday()
  const currency = useBaseCurrency()
  const [editing, setEditing] = useState<Budget | 'new' | null>(null)
  useAddParam(() => setEditing('new'))
  const txs = inCurrency(live.transactions, live.accounts, currency)
  const rows = live.budgets.map((b) => budgetStatus(b, txs, today)).sort((a, b) => b.pct - a.pct)
  const name = (id: string) => live.categories.find((c) => c.id === id)?.name ?? 'Unknown category'
  const color = (id: string) => live.categories.find((c) => c.id === id)?.color ?? undefined
  return (
    <div>
      <PageHeader title="Budgets"><button type="button" className="btn primary" onClick={() => setEditing('new')}><Icon name="plus" size={18} /> Add budget</button></PageHeader>
      {rows.length === 0 ? (
        <div className="card"><EmptyState icon="target" title="No budgets yet" action={<button type="button" className="btn primary" onClick={() => setEditing('new')}>Create a budget</button>}>Set a weekly, monthly or yearly limit for a category and watch your progress. Only expenses in {currency} accounts count.</EmptyState></div>
      ) : (
        <ul className="card list">
          {rows.map((r) => (
            <li key={r.budget.id}>
              <button type="button" className="list-row col" onClick={() => setEditing(r.budget)} aria-label={`Edit ${name(r.budget.categoryId)} budget`}>
                <span className="row between full"><strong>{name(r.budget.categoryId)} <span className="muted small">{r.budget.period}</span></strong><span className={r.over ? 'neg' : ''}>{formatMoney(r.spent, currency)} of {formatMoney(r.limit, currency)}</span></span>
                <ProgressBar pct={r.pct} over={r.over} label={`${name(r.budget.categoryId)} budget used`} color={color(r.budget.categoryId)} />
                <span className="row between full small muted"><span>{r.over ? <strong className="neg">Over by {formatMoney(-r.remaining, currency)}</strong> : `${formatMoney(r.remaining, currency)} left`} · {r.pct}%</span><span>{formatDate(r.from, { month: 'short', day: 'numeric' })} - {formatDate(r.to, { month: 'short', day: 'numeric' })}</span></span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {editing && <BudgetForm existing={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
