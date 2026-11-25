import { useState, type FormEvent } from 'react'
import { useBaseCurrency, useSnapshot, useStore, useToast } from '../context'
import { centsToPlain, formatMoney, parseAmount } from '../../lib/money'
import { newId } from '../../lib/id'
import { ACCOUNT_TYPES, CURRENCIES, MAX_NAME, type Account } from '../../domain/types'
import { assetsAndLiabilities, currenciesInUse } from '../../domain/stats'
import { ComboField, ConfirmDialog, EmptyState, Modal, PageHeader, TextField } from '../components'
import { deleteAccount } from '../../storage/actions'
import { useAddParam } from '../useAddParam'
import { Icon } from '../Icon'

function AccountForm({ existing, onClose }: { existing?: Account; onClose: () => void }) {
  const store = useStore()
  const toast = useToast()
  const base = useBaseCurrency()
  const initial = { name: existing?.name ?? '', type: existing?.type ?? 'Checking', currency: existing?.currency ?? base, balance: existing ? centsToPlain(existing.balanceCents) : '' }
  const [f, setF] = useState(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [confirm, setConfirm] = useState(false)
  const dirty = JSON.stringify(f) !== JSON.stringify(initial)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!f.name.trim()) errs.name = 'Enter a name for the account.'
    else if (f.name.length > MAX_NAME) errs.name = `Keep the name under ${MAX_NAME} characters.`
    if (!f.type.trim()) errs.type = 'Choose or type an account type.'
    else if (f.type.length > 64) errs.type = 'Keep the type under 64 characters.'
    const cur = f.currency.trim().toUpperCase()
    if (!cur) errs.currency = 'Choose or type a currency, for example USD.'
    else if (!/^[A-Z0-9 ._-]{1,16}$/.test(cur)) errs.currency = 'Use a currency code such as USD or EUR.'
    const bal = f.balance.trim() === '' ? 0 : parseAmount(f.balance)
    if (bal === null) errs.balance = 'Enter a number like 1500.00 (negative for money owed). Blank means 0.'
    setErrors(errs)
    if (Object.keys(errs).length) { requestAnimationFrame(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()); return }
    const row: Account = { id: existing?.id ?? newId(), updatedAt: 0, deleted: false, name: f.name.trim(), type: f.type.trim(), currency: cur, balanceCents: bal! }
    await store.upsert('accounts', row)
    toast.show(existing ? 'Account updated.' : 'Account added.')
    onClose()
  }

  async function remove() {
    if (!existing) return
    const err = await deleteAccount(store, existing.id)
    if (err) { setConfirm(false); setErrors({ form: err }); return }
    toast.show('Account deleted.', { action: { label: 'Undo', run: () => { void store.restoreRows('accounts', existing.id) } } })
    onClose()
  }

  return (
    <>
      <Modal title={existing ? 'Edit account' : 'Add account'} onClose={onClose} dirty={dirty}>
        <form onSubmit={submit} noValidate className="stack">
          {errors.form && <p className="error" role="alert">{errors.form}</p>}
          <TextField id="ac-name" label="Name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} error={errors.name} autoFocus autoComplete="off" />
          <ComboField id="ac-type" label="Type" value={f.type} onChange={(e) => setF({ ...f, type: e.target.value })} options={ACCOUNT_TYPES} error={errors.type} hint="Pick one or type your own." />
          <div className="row gap wrap fields-2">
            <ComboField id="ac-cur" label="Currency" value={f.currency} onChange={(e) => setF({ ...f, currency: e.target.value })} options={CURRENCIES} error={errors.currency} />
            <TextField id="ac-bal" label="Balance" inputMode="decimal" value={f.balance} onChange={(e) => setF({ ...f, balance: e.target.value })} error={errors.balance} placeholder="0.00" hint="Blank = 0, negative for debt." />
          </div>
          <p className="muted small">The balance is the number you enter. Adding transactions does not change it (the Android and desktop apps work the same way).</p>
          <div className="row between gap wrap">
            {existing ? <button type="button" className="btn danger-ghost" onClick={() => setConfirm(true)}><Icon name="trash" size={18} /> Delete</button> : <span />}
            <span className="row gap"><button type="button" className="btn" onClick={onClose}>Cancel</button><button type="submit" className="btn primary">{existing ? 'Save changes' : 'Add account'}</button></span>
          </div>
        </form>
      </Modal>
      {confirm && existing && <ConfirmDialog title="Delete account?" body={<>Delete <strong>{existing.name}</strong>? You can undo this right after. An account that still has transactions cannot be deleted.</>} confirmLabel="Delete account" onCancel={() => setConfirm(false)} onConfirm={remove} />}
    </>
  )
}

export default function Accounts() {
  const { live } = useSnapshot()
  const [editing, setEditing] = useState<Account | 'new' | null>(null)
  useAddParam(() => setEditing('new'))
  const currencies = currenciesInUse(live.accounts)
  const counts = new Map<string, number>()
  for (const t of live.transactions) counts.set(t.accountId, (counts.get(t.accountId) ?? 0) + 1)

  return (
    <div>
      <PageHeader title="Accounts"><button type="button" className="btn primary" onClick={() => setEditing('new')}><Icon name="plus" size={18} /> Add account</button></PageHeader>
      {live.accounts.length === 0 ? (
        <div className="card"><EmptyState icon="wallet" title="No accounts yet" action={<button type="button" className="btn primary" onClick={() => setEditing('new')}>Add an account</button>}>Accounts hold your balances: checking, savings, credit cards, cash and more.</EmptyState></div>
      ) : (
        <>
          <section className="stats" aria-label="Net worth">
            {currencies.map((c) => {
              const { assets, liabilities } = assetsAndLiabilities(live.accounts, c)
              return (
                <div className="stat wide" key={c}>
                  <span className="stat-label">Net worth ({c})</span>
                  <strong className={assets - liabilities < 0 ? 'neg' : ''}>{formatMoney(assets - liabilities, c)}</strong>
                  <span className="muted small">Assets {formatMoney(assets, c)} · Liabilities {formatMoney(liabilities, c)}</span>
                </div>
              )
            })}
          </section>
          <ul className="card list">
            {live.accounts.map((a) => (
              <li key={a.id}>
                <button type="button" className="list-row" onClick={() => setEditing(a)} aria-label={`Edit ${a.name}, ${formatMoney(a.balanceCents, a.currency)}`}>
                  <span className="grow"><strong>{a.name}</strong><span className="muted small block">{a.type} · {a.currency} · {counts.get(a.id) ?? 0} transactions</span></span>
                  <span className={a.balanceCents < 0 ? 'amt' : 'amt'}>{formatMoney(a.balanceCents, a.currency)}</span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
      {editing && <AccountForm existing={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
