import { useDeferredValue, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useSnapshot, useStore, useToast, useToday } from '../context'
import { centsToPlain, formatMoney, parseAmount } from '../../lib/money'
import { formatDate, isValidYmd } from '../../lib/dates'
import { newId } from '../../lib/id'
import { descriptionSuggestions, suggestCategory } from '../../domain/categorize'
import { MAX_DESCRIPTION, MAX_NOTES, type Transaction } from '../../domain/types'
import { ComboField, Dot, EmptyState, Modal, PageHeader, Segmented, SelectField, TextField, Field } from '../components'
import { ensureCategory } from '../../storage/actions'
import { useAddParam } from '../useAddParam'
import { Icon } from '../Icon'

const PAGE = 50

interface FormState { kind: 'expense' | 'income'; amount: string; description: string; date: string; accountId: string; category: string; notes: string }

export function TransactionForm({ existing, onClose, defaults }: { existing?: Transaction; onClose: () => void; defaults?: Partial<FormState> }) {
  const { live } = useSnapshot()
  const store = useStore()
  const toast = useToast()
  const today = useToday()
  const catName = (id: string | null) => live.categories.find((c) => c.id === id)?.name ?? ''
  const initial: FormState = existing
    ? { kind: existing.amountCents < 0 ? 'expense' : 'income', amount: centsToPlain(Math.abs(existing.amountCents)), description: existing.description, date: existing.date, accountId: existing.accountId, category: catName(existing.categoryId), notes: existing.notes ?? '' }
    : { kind: 'expense', amount: '', description: '', date: today, accountId: live.accounts[0]?.id ?? '', category: '', notes: '', ...defaults }
  const [f, setF] = useState<FormState>(initial)
  const [touchedCategory, setTouchedCategory] = useState(!!existing)
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({})
  const [saving, setSaving] = useState(false)
  const dirty = JSON.stringify(f) !== JSON.stringify(initial)
  const suggestions = useMemo(() => descriptionSuggestions(live.transactions), [live.transactions])
  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setF((s) => ({ ...s, [k]: v }))

  function onDescription(v: string) {
    setF((s) => {
      const next = { ...s, description: v }
      if (!touchedCategory) {
        const id = suggestCategory(v, live.categories, live.transactions)
        next.category = id ? catName(id) : ''
        if (id && !s.amount) { /* leave amount alone */ }
      }
      return next
    })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    const errs: typeof errors = {}
    const cents = parseAmount(f.amount)
    if (cents === null || cents === 0) errs.amount = f.amount.trim() ? 'Enter an amount like 12.50 (up to two decimals, more than zero).' : 'Enter an amount.'
    else if (cents < 0) errs.amount = 'Enter the amount without a minus sign and pick Expense or Income.'
    if (!f.description.trim()) errs.description = 'Enter a description.'
    else if (f.description.length > MAX_DESCRIPTION) errs.description = `Keep the description under ${MAX_DESCRIPTION} characters.`
    if (!isValidYmd(f.date)) errs.date = 'Pick a valid date.'
    if (!f.accountId || !live.accounts.some((a) => a.id === f.accountId)) errs.accountId = 'Choose an account.'
    if (f.notes.length > MAX_NOTES) errs.notes = `Keep notes under ${MAX_NOTES} characters.`
    setErrors(errs)
    if (Object.keys(errs).length) {
      requestAnimationFrame(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus())
      return
    }
    setSaving(true)
    try {
      const categoryId = await ensureCategory(store, f.category)
      const amountCents = f.kind === 'expense' ? -Math.abs(cents!) : Math.abs(cents!)
      const row: Transaction = existing
        ? { ...existing, amountCents, description: f.description.trim(), date: f.date, accountId: f.accountId, categoryId, notes: f.notes.trim() || null }
        : { id: newId(), updatedAt: 0, deleted: false, createdAt: Date.now(), amountCents, description: f.description.trim(), date: f.date, accountId: f.accountId, categoryId, notes: f.notes.trim() || null }
      await store.upsert('transactions', row)
      toast.show(existing ? 'Transaction updated.' : 'Transaction added.')
      onClose()
    } finally { setSaving(false) }
  }

  async function remove() {
    if (!existing) return
    await store.softDelete('transactions', existing.id)
    toast.show('Transaction deleted.', { action: { label: 'Undo', run: () => { void store.restoreRows('transactions', existing.id) } } })
    onClose()
  }

  if (live.accounts.length === 0) {
    return (
      <Modal title="Add transaction" onClose={onClose}>
        <p>Add an account first, then you can record transactions in it.</p>
        <div className="row end gap"><button type="button" className="btn" onClick={onClose}>Cancel</button><a className="btn primary" href="#/accounts?add=1" onClick={onClose}>Add account</a></div>
      </Modal>
    )
  }

  return (
    <Modal title={existing ? 'Edit transaction' : 'Add transaction'} onClose={onClose} dirty={dirty}>
      <form onSubmit={submit} noValidate className="stack">
        <Segmented label="Type" value={f.kind} onChange={(v) => set('kind', v)} options={[{ value: 'expense', label: 'Expense' }, { value: 'income', label: 'Income' }]} />
        <TextField id="tx-amount" label="Amount" inputMode="decimal" autoComplete="off" value={f.amount} onChange={(e) => set('amount', e.target.value)} error={errors.amount} placeholder="0.00" autoFocus />
        <ComboField id="tx-desc" label="Description" value={f.description} onChange={(e) => onDescription(e.target.value)} options={suggestions} error={errors.description} maxLength={MAX_DESCRIPTION + 50} placeholder="e.g. Groceries" />
        <div className="row gap wrap fields-2">
          <TextField id="tx-date" label="Date" type="date" value={f.date} onChange={(e) => set('date', e.target.value)} error={errors.date} />
          <SelectField id="tx-acct" label="Account" value={f.accountId} onChange={(v) => set('accountId', v)} error={errors.accountId}>
            {live.accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </SelectField>
        </div>
        <ComboField id="tx-cat" label="Category" value={f.category} onChange={(e) => { setTouchedCategory(true); set('category', e.target.value) }} options={live.categories.map((c) => c.name)}
          hint={touchedCategory ? 'Pick one or type a new category name.' : 'Suggested from the description; change it if you like. Type a new name to create a category.'} />
        <Field label="Notes (optional)" htmlFor="tx-notes" error={errors.notes}>
          <textarea id="tx-notes" rows={2} value={f.notes} onChange={(e) => set('notes', e.target.value)} aria-invalid={errors.notes ? true : undefined} />
        </Field>
        <div className="row between gap wrap">
          {existing ? <button type="button" className="btn danger-ghost" onClick={remove}><Icon name="trash" size={18} /> Delete</button> : <span />}
          <span className="row gap">
            <button type="button" className="btn" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn primary" disabled={saving}>{existing ? 'Save changes' : 'Add transaction'}</button>
          </span>
        </div>
      </form>
    </Modal>
  )
}

export default function Transactions() {
  const { live } = useSnapshot()
  const [editing, setEditing] = useState<Transaction | 'new' | null>(null)
  const [search, setSearch] = useState('')
  const [account, setAccount] = useState('')
  const [category, setCategory] = useState('')
  const [kind, setKind] = useState('')
  const [shown, setShown] = useState(PAGE)
  const deferred = useDeferredValue(search)
  useAddParam(() => setEditing('new'))

  const accName = useMemo(() => new Map(live.accounts.map((a) => [a.id, a])), [live.accounts])
  const cats = useMemo(() => new Map(live.categories.map((c) => [c.id, c])), [live.categories])

  const filtered = useMemo(() => {
    const q = deferred.trim().toLowerCase()
    return live.transactions
      .filter((t) => (!account || t.accountId === account) && (!category || (category === '_none' ? !t.categoryId || !cats.has(t.categoryId) : t.categoryId === category))
        && (!kind || (kind === 'expense' ? t.amountCents < 0 : t.amountCents > 0))
        && (!q || t.description.toLowerCase().includes(q) || (t.notes ?? '').toLowerCase().includes(q) || (cats.get(t.categoryId ?? '')?.name ?? '').toLowerCase().includes(q) || centsToPlain(Math.abs(t.amountCents)).includes(q)))
      .sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : b.createdAt - a.createdAt))
  }, [live.transactions, deferred, account, category, kind, cats])

  useEffect(() => setShown(PAGE), [deferred, account, category, kind])
  const visible = filtered.slice(0, shown)
  const filtering = !!(deferred || account || category || kind)

  return (
    <div>
      <PageHeader title="Transactions"><button type="button" className="btn primary" onClick={() => setEditing('new')}><Icon name="plus" size={18} /> Add transaction</button></PageHeader>
      {live.transactions.length > 0 && (
        <div className="filters card" role="search" aria-label="Filter transactions">
          <div className="field grow2"><label htmlFor="f-search">Search</label><input id="f-search" type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Description, notes, category or amount" autoComplete="off" /></div>
          <SelectField id="f-acct" label="Account" value={account} onChange={setAccount}><option value="">All accounts</option>{live.accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</SelectField>
          <SelectField id="f-cat" label="Category" value={category} onChange={setCategory}><option value="">All categories</option><option value="_none">Uncategorized</option>{live.categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</SelectField>
          <SelectField id="f-kind" label="Type" value={kind} onChange={setKind}><option value="">Income and expenses</option><option value="expense">Expenses</option><option value="income">Income</option></SelectField>
        </div>
      )}
      {live.transactions.length === 0 ? (
        <div className="card"><EmptyState icon="list" title="No transactions yet" action={<button type="button" className="btn primary" onClick={() => setEditing('new')}>Add your first transaction</button>}>Record income and spending here, or import a CSV or a backup from the Import / Export page.</EmptyState></div>
      ) : filtered.length === 0 ? (
        <div className="card"><EmptyState icon="search" title="Nothing matches" action={filtering ? <button type="button" className="btn" onClick={() => { setSearch(''); setAccount(''); setCategory(''); setKind('') }}>Clear filters</button> : undefined}>No transactions match these filters.</EmptyState></div>
      ) : (
        <>
          <p className="muted small" role="status">{filtered.length.toLocaleString()} transaction{filtered.length === 1 ? '' : 's'}{filtering ? ' match' : ''}</p>
          <ul className="card list tx-list">
            {visible.map((t) => {
              const acc = accName.get(t.accountId)
              const c = t.categoryId ? cats.get(t.categoryId) : undefined
              return (
                <li key={t.id}>
                  <button type="button" className="list-row" onClick={() => setEditing(t)} aria-label={`Edit ${t.description}, ${formatMoney(t.amountCents, acc?.currency)}, ${formatDate(t.date)}`}>
                    <Dot color={c?.color} />
                    <span className="grow">
                      <strong className="ellipsis">{t.description}</strong>
                      <span className="muted small block ellipsis">{formatDate(t.date)} · {acc?.name ?? 'Unknown account'} · {c?.name ?? 'Uncategorized'}</span>
                    </span>
                    <span className={t.amountCents < 0 ? 'amt' : 'amt pos'}>{formatMoney(t.amountCents, acc?.currency ?? 'USD')}</span>
                  </button>
                </li>
              )
            })}
          </ul>
          {shown < filtered.length && (
            <div className="row center"><button type="button" className="btn" onClick={() => setShown((s) => s + PAGE)}>Show {Math.min(PAGE, filtered.length - shown)} more</button></div>
          )}
        </>
      )}
      {editing && <TransactionForm existing={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
