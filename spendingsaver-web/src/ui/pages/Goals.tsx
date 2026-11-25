import { useState, type FormEvent } from 'react'
import { useBaseCurrency, useSnapshot, useStore, useToast, useToday } from '../context'
import { centsToPlain, formatMoney, parseAmount, percent } from '../../lib/money'
import { formatDate, isValidYmd } from '../../lib/dates'
import { newId } from '../../lib/id'
import { goalMonthlyNeedCents } from '../../domain/payoff'
import type { Goal } from '../../domain/types'
import { EmptyState, Modal, PageHeader, ProgressBar, TextField } from '../components'
import { useAddParam } from '../useAddParam'
import { Icon } from '../Icon'

function GoalForm({ existing, onClose }: { existing?: Goal; onClose: () => void }) {
  const store = useStore()
  const toast = useToast()
  const initial = { name: existing?.name ?? '', target: existing ? centsToPlain(existing.targetCents) : '', saved: existing ? centsToPlain(existing.savedCents) : '', due: existing?.due ?? '' }
  const [f, setF] = useState(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const dirty = JSON.stringify(f) !== JSON.stringify(initial)
  async function submit(e: FormEvent) {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!f.name.trim()) errs.name = 'Enter a name, for example Emergency fund.'
    const target = parseAmount(f.target)
    if (target === null || target <= 0) errs.target = 'Enter a target greater than zero.'
    const saved = f.saved.trim() === '' ? 0 : parseAmount(f.saved)
    if (saved === null || saved < 0) errs.saved = 'Enter what you have saved so far (0 or more).'
    if (f.due && !isValidYmd(f.due)) errs.due = 'Pick a valid date or leave it blank.'
    setErrors(errs)
    if (Object.keys(errs).length) { requestAnimationFrame(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()); return }
    await store.upsert('goals', { id: existing?.id ?? newId(), updatedAt: 0, deleted: false, name: f.name.trim(), targetCents: target!, savedCents: saved!, due: f.due || null })
    toast.show(existing ? 'Goal updated.' : 'Goal added.')
    onClose()
  }
  return (
    <Modal title={existing ? 'Edit goal' : 'Add goal'} onClose={onClose} dirty={dirty}>
      <form onSubmit={submit} noValidate className="stack">
        <TextField id="go-name" label="Name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} error={errors.name} autoFocus autoComplete="off" />
        <div className="row gap wrap fields-2">
          <TextField id="go-target" label="Target" inputMode="decimal" value={f.target} onChange={(e) => setF({ ...f, target: e.target.value })} error={errors.target} placeholder="0.00" />
          <TextField id="go-saved" label="Saved so far" inputMode="decimal" value={f.saved} onChange={(e) => setF({ ...f, saved: e.target.value })} error={errors.saved} placeholder="0.00" />
        </div>
        <TextField id="go-due" label="Target date (optional)" type="date" value={f.due} onChange={(e) => setF({ ...f, due: e.target.value })} error={errors.due} />
        <div className="row between gap wrap">
          {existing ? <button type="button" className="btn danger-ghost" onClick={async () => { await store.softDelete('goals', existing.id); toast.show('Goal deleted.', { action: { label: 'Undo', run: () => { void store.restoreRows('goals', existing.id) } } }); onClose() }}><Icon name="trash" size={18} /> Delete</button> : <span />}
          <span className="row gap"><button type="button" className="btn" onClick={onClose}>Cancel</button><button type="submit" className="btn primary">{existing ? 'Save changes' : 'Add goal'}</button></span>
        </div>
      </form>
    </Modal>
  )
}

function Contribute({ goal, onClose }: { goal: Goal; onClose: () => void }) {
  const store = useStore()
  const toast = useToast()
  const [v, setV] = useState('')
  const [err, setErr] = useState<string | null>(null)
  return (
    <Modal title={`Add savings to ${goal.name}`} onClose={onClose} dirty={v !== ''}>
      <form className="stack" noValidate onSubmit={async (e) => {
        e.preventDefault()
        const c = parseAmount(v)
        if (c === null || c === 0) return setErr('Enter an amount like 50.00. Use a minus sign to take money out.')
        await store.upsert('goals', { ...goal, savedCents: Math.max(0, goal.savedCents + c) })
        toast.show('Savings updated.')
        onClose()
      }}>
        <TextField id="co-amt" label="Amount" inputMode="decimal" value={v} onChange={(e) => { setV(e.target.value); setErr(null) }} error={err} autoFocus placeholder="0.00" />
        <p className="muted small">This only updates the goal's saved amount. It does not create a transaction or change an account balance.</p>
        <div className="row end gap"><button type="button" className="btn" onClick={onClose}>Cancel</button><button type="submit" className="btn primary">Add</button></div>
      </form>
    </Modal>
  )
}

export default function Goals() {
  const { live } = useSnapshot()
  const today = useToday()
  const currency = useBaseCurrency()
  const [editing, setEditing] = useState<Goal | 'new' | null>(null)
  const [adding, setAdding] = useState<Goal | null>(null)
  useAddParam(() => setEditing('new'))
  const m = (c: number) => formatMoney(c, currency)
  return (
    <div>
      <PageHeader title="Savings goals"><button type="button" className="btn primary" onClick={() => setEditing('new')}><Icon name="plus" size={18} /> Add goal</button></PageHeader>
      <p className="muted">Goals are tracked here by hand and stay in this browser (they are not part of the backup file). Amounts are shown in {currency}.</p>
      {live.goals.length === 0 ? <div className="card"><EmptyState icon="flag" title="No goals yet" action={<button type="button" className="btn primary" onClick={() => setEditing('new')}>Add a goal</button>}>Save for a trip, an emergency fund or a down payment.</EmptyState></div> : (
        <ul className="card list">
          {live.goals.map((g) => {
            const pct = percent(g.savedCents, g.targetCents)
            const need = goalMonthlyNeedCents(g, today)
            return (
              <li key={g.id} className="list-row static col">
                <span className="row between full"><strong>{g.name}</strong><span>{m(g.savedCents)} of {m(g.targetCents)}</span></span>
                <ProgressBar pct={pct} label={`${g.name} progress`} />
                <span className="row between full small muted wrap gap">
                  <span>{pct}%{g.due ? ` · by ${formatDate(g.due)}` : ''}{need != null ? ` · ${m(need)} a month needed` : ''}{pct >= 100 ? ' · reached' : ''}</span>
                  <span className="row gap"><button type="button" className="btn small" onClick={() => setAdding(g)}>Add savings</button><button type="button" className="btn small" onClick={() => setEditing(g)} aria-label={`Edit ${g.name}`}>Edit</button></span>
                </span>
              </li>
            )
          })}
        </ul>
      )}
      {editing && <GoalForm existing={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
      {adding && <Contribute goal={adding} onClose={() => setAdding(null)} />}
    </div>
  )
}
