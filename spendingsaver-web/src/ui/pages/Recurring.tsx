import { useState, type FormEvent } from 'react'
import { useBaseCurrency, useSnapshot, useStore, useToast, useToday } from '../context'
import { centsToPlain, formatMoney, parseAmount } from '../../lib/money'
import { formatDate, isValidYmd } from '../../lib/dates'
import { newId } from '../../lib/id'
import { FREQUENCIES, monthlyEquivalentCents } from '../../domain/recurring'
import type { Frequency, Recurring } from '../../domain/types'
import { ComboField, EmptyState, Modal, PageHeader, Segmented, SelectField, TextField } from '../components'
import { ensureCategory, postDueRecurring } from '../../storage/actions'
import { useAddParam } from '../useAddParam'
import { Icon } from '../Icon'

function RecurringForm({ existing, onClose }: { existing?: Recurring; onClose: () => void }) {
  const store = useStore()
  const toast = useToast()
  const today = useToday()
  const { live } = useSnapshot()
  const catName = (id: string | null) => live.categories.find((c) => c.id === id)?.name ?? ''
  const initial = {
    name: existing?.name ?? '', kind: existing && existing.amountCents > 0 ? 'income' : 'expense', amount: existing ? centsToPlain(Math.abs(existing.amountCents)) : '',
    accountId: existing?.accountId ?? live.accounts[0]?.id ?? '', category: catName(existing?.categoryId ?? null), frequency: (existing?.frequency ?? 'monthly') as Frequency, nextDate: existing?.nextDate ?? today, active: existing?.active ?? true,
  }
  const [f, setF] = useState(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const dirty = JSON.stringify(f) !== JSON.stringify(initial)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!f.name.trim()) errs.name = 'Enter a name, for example Rent.'
    const cents = parseAmount(f.amount)
    if (cents === null || cents <= 0) errs.amount = 'Enter an amount greater than zero.'
    if (!live.accounts.some((a) => a.id === f.accountId)) errs.accountId = 'Choose an account.'
    if (!isValidYmd(f.nextDate)) errs.nextDate = 'Pick a valid date.'
    setErrors(errs)
    if (Object.keys(errs).length) { requestAnimationFrame(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()); return }
    const categoryId = await ensureCategory(store, f.category)
    await store.upsert('recurring', { id: existing?.id ?? newId(), updatedAt: 0, deleted: false, name: f.name.trim(), amountCents: f.kind === 'expense' ? -cents! : cents!, accountId: f.accountId, categoryId, frequency: f.frequency, nextDate: f.nextDate, active: f.active })
    toast.show(existing ? 'Recurring item updated.' : 'Recurring item added.')
    onClose()
    void postDueRecurring(store)
  }

  return (
    <Modal title={existing ? 'Edit recurring item' : 'Add recurring item'} onClose={onClose} dirty={dirty}>
      <form onSubmit={submit} noValidate className="stack">
        <TextField id="re-name" label="Name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} error={errors.name} autoFocus autoComplete="off" />
        <Segmented label="Type" value={f.kind as 'expense' | 'income'} onChange={(v) => setF({ ...f, kind: v })} options={[{ value: 'expense', label: 'Bill or expense' }, { value: 'income', label: 'Income' }]} />
        <div className="row gap wrap fields-2">
          <TextField id="re-amt" label="Amount" inputMode="decimal" value={f.amount} onChange={(e) => setF({ ...f, amount: e.target.value })} error={errors.amount} placeholder="0.00" />
          <SelectField id="re-freq" label="Repeats" value={f.frequency} onChange={(v) => setF({ ...f, frequency: v as Frequency })}>{FREQUENCIES.map((x) => <option key={x} value={x}>{x[0].toUpperCase() + x.slice(1)}</option>)}</SelectField>
        </div>
        <div className="row gap wrap fields-2">
          <SelectField id="re-acct" label="Account" value={f.accountId} onChange={(v) => setF({ ...f, accountId: v })} error={errors.accountId}>{live.accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</SelectField>
          <TextField id="re-date" label="Next date" type="date" value={f.nextDate} onChange={(e) => setF({ ...f, nextDate: e.target.value })} error={errors.nextDate} hint="Past dates post immediately, once per missed occurrence." />
        </div>
        <ComboField id="re-cat" label="Category" value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })} options={live.categories.map((c) => c.name)} />
        <label className="check"><input type="checkbox" checked={f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} /> Active</label>
        <div className="row between gap wrap">
          {existing ? <button type="button" className="btn danger-ghost" onClick={async () => { await store.softDelete('recurring', existing.id); toast.show('Recurring item deleted.', { action: { label: 'Undo', run: () => { void store.restoreRows('recurring', existing.id) } } }); onClose() }}><Icon name="trash" size={18} /> Delete</button> : <span />}
          <span className="row gap"><button type="button" className="btn" onClick={onClose}>Cancel</button><button type="submit" className="btn primary">{existing ? 'Save changes' : 'Add item'}</button></span>
        </div>
      </form>
    </Modal>
  )
}

export default function RecurringPage() {
  const { live } = useSnapshot()
  const store = useStore()
  const toast = useToast()
  const currency = useBaseCurrency()
  const [editing, setEditing] = useState<Recurring | 'new' | null>(null)
  useAddParam(() => setEditing('new'))
  const accounts = new Map(live.accounts.map((a) => [a.id, a]))
  const active = live.recurring.filter((r) => r.active && accounts.get(r.accountId)?.currency.toUpperCase() === currency)
  const monthly = active.reduce((s, r) => s + monthlyEquivalentCents(r), 0)
  const sorted = [...live.recurring].sort((a, b) => (a.nextDate < b.nextDate ? -1 : 1))
  return (
    <div>
      <PageHeader title="Recurring"><button type="button" className="btn primary" disabled={live.accounts.length === 0} onClick={() => setEditing('new')}><Icon name="plus" size={18} /> Add item</button></PageHeader>
      <p className="muted">Bills and income that post a transaction automatically when they come due. This happens when you open the app, so a missed day is caught up next time. Recurring items stay in this browser and are not part of the backup file.</p>
      {live.accounts.length === 0 ? <div className="card"><EmptyState icon="repeat" title="Add an account first">Recurring items post into an account.</EmptyState></div>
        : sorted.length === 0 ? <div className="card"><EmptyState icon="repeat" title="No recurring items" action={<button type="button" className="btn primary" onClick={() => setEditing('new')}>Add one</button>}>Rent, subscriptions, salary: add them once.</EmptyState></div> : (
          <>
            <div className="stats"><div className="stat wide"><span className="stat-label">Net per month (active, {currency})</span><strong className={monthly < 0 ? 'neg' : 'pos'}>{formatMoney(monthly, currency)}</strong></div></div>
            <ul className="card list">
              {sorted.map((r) => (
                <li key={r.id}>
                  <button type="button" className="list-row" onClick={() => setEditing(r)} aria-label={`Edit ${r.name}`}>
                    <span className="grow"><strong>{r.name}</strong><span className="muted small block">{r.frequency} · next {formatDate(r.nextDate)} · {accounts.get(r.accountId)?.name ?? 'Unknown account'}{r.active ? '' : ' · paused'}</span></span>
                    <span className={r.amountCents < 0 ? 'amt' : 'amt pos'}>{formatMoney(r.amountCents, accounts.get(r.accountId)?.currency ?? currency)}</span>
                  </button>
                </li>
              ))}
            </ul>
            <div className="row"><button type="button" className="btn" onClick={() => { void postDueRecurring(store).then((n) => toast.show(n ? `Posted ${n} transaction${n === 1 ? '' : 's'}.` : 'Nothing is due yet.')) }}>Post anything that is due now</button></div>
          </>
        )}
      {editing && <RecurringForm existing={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
