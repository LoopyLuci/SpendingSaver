import { useMemo, useState, type FormEvent } from 'react'
import { useBaseCurrency, useSnapshot, useStore, useToast, useToday } from '../context'
import { centsToPlain, formatMoney, parseAmount } from '../../lib/money'
import { formatDate } from '../../lib/dates'
import { newId } from '../../lib/id'
import { payoffDate, payoffSchedule, simulateStrategy } from '../../domain/payoff'
import type { Debt } from '../../domain/types'
import { EmptyState, Modal, PageHeader, TextField } from '../components'
import { useAddParam } from '../useAddParam'
import { Icon } from '../Icon'

function DebtForm({ existing, onClose }: { existing?: Debt; onClose: () => void }) {
  const store = useStore()
  const toast = useToast()
  const initial = { name: existing?.name ?? '', balance: existing ? centsToPlain(existing.balanceCents) : '', apr: existing ? String(existing.apr) : '', min: existing ? centsToPlain(existing.minPaymentCents) : '' }
  const [f, setF] = useState(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const dirty = JSON.stringify(f) !== JSON.stringify(initial)
  async function submit(e: FormEvent) {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!f.name.trim()) errs.name = 'Enter a name, for example Credit card.'
    const bal = parseAmount(f.balance)
    if (bal === null || bal < 0) errs.balance = 'Enter the amount you owe (0 or more).'
    const aprText = f.apr.trim() === '' ? '0' : f.apr.trim().replace(',', '.')
    const apr = Number(aprText)
    if (!/^\d+(\.\d+)?$/.test(aprText) || apr > 100) errs.apr = 'Enter the yearly interest rate as a percentage from 0 to 100.'
    const min = f.min.trim() === '' ? 0 : parseAmount(f.min)
    if (min === null || min < 0) errs.min = 'Enter the monthly payment (0 or more).'
    setErrors(errs)
    if (Object.keys(errs).length) { requestAnimationFrame(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()); return }
    await store.upsert('debts', { id: existing?.id ?? newId(), updatedAt: 0, deleted: false, name: f.name.trim(), balanceCents: bal!, apr, minPaymentCents: min! })
    toast.show(existing ? 'Debt updated.' : 'Debt added.')
    onClose()
  }
  return (
    <Modal title={existing ? 'Edit debt' : 'Add debt'} onClose={onClose} dirty={dirty}>
      <form onSubmit={submit} noValidate className="stack">
        <TextField id="de-name" label="Name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} error={errors.name} autoFocus autoComplete="off" />
        <div className="row gap wrap fields-2">
          <TextField id="de-bal" label="Balance owed" inputMode="decimal" value={f.balance} onChange={(e) => setF({ ...f, balance: e.target.value })} error={errors.balance} placeholder="0.00" />
          <TextField id="de-apr" label="Interest rate (APR %)" inputMode="decimal" value={f.apr} onChange={(e) => setF({ ...f, apr: e.target.value })} error={errors.apr} placeholder="0" />
        </div>
        <TextField id="de-min" label="Monthly payment" inputMode="decimal" value={f.min} onChange={(e) => setF({ ...f, min: e.target.value })} error={errors.min} placeholder="0.00" />
        <div className="row between gap wrap">
          {existing ? <button type="button" className="btn danger-ghost" onClick={async () => { await store.softDelete('debts', existing.id); toast.show('Debt deleted.', { action: { label: 'Undo', run: () => { void store.restoreRows('debts', existing.id) } } }); onClose() }}><Icon name="trash" size={18} /> Delete</button> : <span />}
          <span className="row gap"><button type="button" className="btn" onClick={onClose}>Cancel</button><button type="submit" className="btn primary">{existing ? 'Save changes' : 'Add debt'}</button></span>
        </div>
      </form>
    </Modal>
  )
}

function Calculator({ currency }: { currency: string }) {
  const today = useToday()
  const [bal, setBal] = useState('5000')
  const [apr, setApr] = useState('19.9')
  const [pay, setPay] = useState('200')
  const [extra, setExtra] = useState('50')
  const m = (c: number) => formatMoney(c, currency)
  const b = parseAmount(bal), p = parseAmount(pay), e = extra.trim() ? parseAmount(extra) : 0
  const a = Number(apr.replace(',', '.'))
  const valid = b !== null && b > 0 && p !== null && p > 0 && e !== null && e >= 0 && Number.isFinite(a) && a >= 0 && a <= 100
  const base = valid ? payoffSchedule(b!, a, p!) : null
  const faster = valid && e! > 0 ? payoffSchedule(b!, a, p! + e!) : null
  return (
    <section className="card" aria-labelledby="h-calc">
      <h2 id="h-calc">Payoff calculator</h2>
      <p className="muted small">Try numbers without saving anything. Interest is charged monthly at APR / 12.</p>
      <div className="grid-4">
        <TextField id="ca-bal" label="Balance" inputMode="decimal" value={bal} onChange={(x) => setBal(x.target.value)} />
        <TextField id="ca-apr" label="APR %" inputMode="decimal" value={apr} onChange={(x) => setApr(x.target.value)} />
        <TextField id="ca-pay" label="Monthly payment" inputMode="decimal" value={pay} onChange={(x) => setPay(x.target.value)} />
        <TextField id="ca-extra" label="Extra per month" inputMode="decimal" value={extra} onChange={(x) => setExtra(x.target.value)} />
      </div>
      <div aria-live="polite">
        {!valid ? <p className="error">Enter a balance, a rate from 0 to 100, and a payment greater than zero.</p>
          : !base ? <p className="error">A payment of {m(p!)} does not cover the monthly interest, so this balance would never be paid off. Increase the payment.</p>
            : (
              <div className="stats">
                <div className="stat"><span className="stat-label">Paid off in</span><strong>{base.months} months</strong><span className="muted small">{formatDate(payoffDate(today, base.months), { month: 'short', year: 'numeric' })}</span></div>
                <div className="stat"><span className="stat-label">Total interest</span><strong>{m(base.interestCents)}</strong></div>
                {faster && <div className="stat"><span className="stat-label">With {m(e!)} extra</span><strong>{faster.months} months</strong><span className="muted small">saves {m(base.interestCents - faster.interestCents)} interest, {base.months - faster.months} months sooner</span></div>}
              </div>
            )}
      </div>
    </section>
  )
}

export default function Debts() {
  const { live } = useSnapshot()
  const today = useToday()
  const currency = useBaseCurrency()
  const [editing, setEditing] = useState<Debt | 'new' | null>(null)
  const [budget, setBudget] = useState('')
  useAddParam(() => setEditing('new'))
  const m = (c: number) => formatMoney(c, currency)
  const minTotal = live.debts.reduce((s, d) => s + d.minPaymentCents, 0)
  const budgetCents = budget.trim() ? parseAmount(budget) : minTotal
  const plans = useMemo(() => {
    const input = live.debts.map((d) => ({ id: d.id, name: d.name, balanceCents: d.balanceCents, apr: d.apr, minPaymentCents: d.minPaymentCents }))
    const total = Math.max(budgetCents ?? minTotal, minTotal)
    return live.debts.length >= 2 ? { av: simulateStrategy(input, total, 'avalanche'), sn: simulateStrategy(input, total, 'snowball') } : null
  }, [live.debts, budgetCents, minTotal])

  return (
    <div>
      <PageHeader title="Debt payoff"><button type="button" className="btn primary" onClick={() => setEditing('new')}><Icon name="plus" size={18} /> Add debt</button></PageHeader>
      <Calculator currency={currency} />
      <section aria-labelledby="h-debts">
        <h2 id="h-debts" className="section-title">Your debts</h2>
        <p className="muted small">Debts are tracked here by hand and stay in this browser (not in the backup file). Amounts are shown in {currency}.</p>
        {live.debts.length === 0 ? <div className="card"><EmptyState icon="chart" title="No debts recorded" action={<button type="button" className="btn primary" onClick={() => setEditing('new')}>Add a debt</button>}>Add a card or loan to see when it will be paid off.</EmptyState></div> : (
          <ul className="card list">
            {live.debts.map((d) => {
              const p = payoffSchedule(d.balanceCents, d.apr, d.minPaymentCents)
              return (
                <li key={d.id}>
                  <button type="button" className="list-row" onClick={() => setEditing(d)} aria-label={`Edit ${d.name}`}>
                    <span className="grow"><strong>{d.name}</strong>
                      <span className="muted small block">{d.apr}% APR · {m(d.minPaymentCents)}/month · {!p ? 'payment does not cover the interest' : p.months === 0 ? 'paid off' : `paid off ${formatDate(payoffDate(today, p.months), { month: 'short', year: 'numeric' })} (${p.months} months), ${m(p.interestCents)} interest`}</span></span>
                    <span className="amt">{m(d.balanceCents)}</span>
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </section>
      {plans && (
        <section className="card" aria-labelledby="h-strat">
          <h2 id="h-strat">Payoff order</h2>
          <TextField id="st-bud" label={`Total monthly budget for all debts (at least ${m(minTotal)})`} inputMode="decimal" value={budget} onChange={(e) => setBudget(e.target.value)} placeholder={centsToPlain(minTotal)} error={budget.trim() && budgetCents === null ? 'Enter an amount like 600.00.' : null} />
          {(['av', 'sn'] as const).map((k) => {
            const r = plans[k]
            return (
              <p key={k}><strong>{k === 'av' ? 'Avalanche (highest interest first)' : 'Snowball (smallest balance first)'}:</strong>{' '}
                {r.feasible ? `debt-free in ${r.months} months, ${m(r.interestCents)} total interest. Order: ${r.order.join(', ')}.` : 'payments do not cover the interest on every debt.'}</p>
            )
          })}
        </section>
      )}
      {editing && <DebtForm existing={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
