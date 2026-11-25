import { useEffect, useMemo, useState } from 'react'
import { useSnapshot, useStore } from '../context'
import { FILING_LABELS, NO_INCOME_TAX_STATES, SUPPORTED_YEARS, defaultTaxProfile, estimateTax, quarterlyDates, type FilingStatus, type TaxProfile } from '../../domain/tax'
import { PageHeader, SelectField, TextField } from '../components'

const usd = new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 0 })
const usd2 = new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 })
const pct = (x: number) => `${(x * 100).toFixed(1)}%`

const MONEY_FIELDS: { key: keyof TaxProfile; label: string; hint?: string }[] = [
  { key: 'wages', label: 'W-2 wages' },
  { key: 'selfEmployment', label: 'Self-employment profit', hint: 'After business expenses.' },
  { key: 'otherIncome', label: 'Other income', hint: 'Interest, dividends, etc.' },
  { key: 'adjustments', label: 'Adjustments', hint: 'IRA or HSA contributions, etc.' },
  { key: 'federalWithheld', label: 'Federal tax withheld' },
  { key: 'estimatedPaid', label: 'Estimated payments made' },
]

function num(text: string): number {
  const n = Number(text.replace(/[,$\s]/g, ''))
  return Number.isFinite(n) && n >= 0 ? Math.min(n, 1e10) : 0
}

export default function Tax() {
  const store = useStore()
  const { settings } = useSnapshot()
  const saved = (settings.taxProfile ?? null) as Partial<TaxProfile> | null
  const [p, setP] = useState<TaxProfile>(() => ({ ...defaultTaxProfile(), ...(saved ?? {}) }))
  const [text, setText] = useState<Record<string, string>>(() => Object.fromEntries(MONEY_FIELDS.map((f) => [f.key, p[f.key] ? String(p[f.key]) : ''])))
  const r = useMemo(() => estimateTax(p), [p])
  useEffect(() => { const t = window.setTimeout(() => { void store.updateSettings({ taxProfile: p }) }, 600); return () => window.clearTimeout(t) }, [p, store])
  const set = (patch: Partial<TaxProfile>) => setP((x) => ({ ...x, ...patch }))
  const owes = r.balance >= 0

  return (
    <div>
      <PageHeader title="Tax estimate" />
      <p className="muted">A rough US federal estimate for planning, calculated here with the same formulas and 2025/2026 figures as the Android app. It is not tax advice and not a filing: it leaves out the qualified business income deduction, AMT, capital-gain rates, most credits and itemized-deduction limits. State tax uses the single rate you enter. Inputs are saved in this browser only.</p>
      <div className="grid-2">
        <section className="card" aria-labelledby="h-in">
          <h2 id="h-in">Your numbers (dollars per year)</h2>
          <div className="row gap wrap fields-2">
            <SelectField id="tx-year" label="Tax year" value={String(p.year)} onChange={(v) => set({ year: Number(v) })}>{SUPPORTED_YEARS.map((y) => <option key={y} value={y}>{y}</option>)}</SelectField>
            <SelectField id="tx-fil" label="Filing status" value={p.filing} onChange={(v) => set({ filing: v as FilingStatus })}>{(Object.keys(FILING_LABELS) as FilingStatus[]).map((k) => <option key={k} value={k}>{FILING_LABELS[k]}</option>)}</SelectField>
          </div>
          <div className="row gap wrap fields-2">
            {MONEY_FIELDS.map((f) => (
              <TextField key={f.key} id={`tx-${f.key}`} label={f.label} hint={f.hint} inputMode="decimal" value={text[f.key] ?? ''} placeholder="0"
                onChange={(e) => { setText((t) => ({ ...t, [f.key]: e.target.value })); set({ [f.key]: num(e.target.value) } as Partial<TaxProfile>) }} />
            ))}
          </div>
          <label className="check"><input type="checkbox" checked={p.useItemized} onChange={(e) => set({ useItemized: e.target.checked })} /> Itemize deductions instead of the standard deduction</label>
          {p.useItemized && <TextField id="tx-item" label="Itemized deductions" inputMode="decimal" value={text.itemized ?? (p.itemized ? String(p.itemized) : '')} onChange={(e) => { setText((t) => ({ ...t, itemized: e.target.value })); set({ itemized: num(e.target.value) }) }} />}
          <div className="row gap wrap fields-2">
            <TextField id="tx-kids" label="Children under 17" type="number" min={0} max={20} value={String(p.children)} onChange={(e) => set({ children: Math.max(0, Math.min(20, Math.floor(Number(e.target.value) || 0))) })} />
            <TextField id="tx-state" label="State (2 letters, optional)" value={p.state} maxLength={2} onChange={(e) => set({ state: e.target.value.toUpperCase().replace(/[^A-Z]/g, '') })} hint={NO_INCOME_TAX_STATES.has(p.state) ? 'No state income tax on wages.' : undefined} />
          </div>
          <TextField id="tx-rate" label="State effective rate (%)" inputMode="decimal" value={String(p.stateRatePercent || '')} placeholder="0" onChange={(e) => set({ stateRatePercent: Math.min(20, num(e.target.value)) })} hint="Percent of taxable income. Look this up for your state." />
        </section>
        <section className="card" aria-labelledby="h-out" aria-live="polite">
          <h2 id="h-out">Estimate</h2>
          <div className="stats one">
            <div className="stat wide"><span className="stat-label">{owes ? 'Estimated amount still owed' : 'Estimated refund'}</span><strong className={owes ? 'neg' : 'pos'}>{usd.format(Math.abs(r.balance))}</strong></div>
          </div>
          <dl className="kv">
            <dt>Gross income</dt><dd>{usd2.format(r.grossIncome)}</dd>
            <dt>Adjusted gross income</dt><dd>{usd2.format(r.adjustedGross)}</dd>
            <dt>{p.useItemized ? 'Itemized deductions' : 'Standard deduction'}</dt><dd>{usd2.format(r.deduction)}</dd>
            <dt>Taxable income</dt><dd>{usd2.format(r.taxableIncome)}</dd>
            <dt>Income tax before credits</dt><dd>{usd2.format(r.incomeTaxBeforeCredits)}</dd>
            <dt>Child tax credit</dt><dd>-{usd2.format(r.childCredit)}</dd>
            <dt>Income tax</dt><dd>{usd2.format(r.incomeTax)}</dd>
            <dt>Self-employment tax</dt><dd>{usd2.format(r.selfEmploymentTax)}</dd>
            <dt>Additional Medicare tax</dt><dd>{usd2.format(r.additionalMedicare)}</dd>
            <dt>State tax (your rate)</dt><dd>{usd2.format(r.stateTax)}</dd>
            <dt>Total due before payments</dt><dd><strong>{usd2.format(r.dueBeforePayments)}</strong></dd>
            <dt>Already paid</dt><dd>{usd2.format(r.totalPaid)}</dd>
            <dt>Marginal bracket</dt><dd>{pct(r.marginalRate)}</dd>
            <dt>Effective rate incl. payroll tax</dt><dd>{pct(r.effectiveRate)}</dd>
            <dt>Employee Social Security and Medicare (withheld by employer)</dt><dd>{usd2.format(r.employeeFica)}</dd>
          </dl>
          {r.quarterly > 0 && <p>Suggested quarterly estimated payment: <strong>{usd2.format(r.quarterly)}</strong> due {quarterlyDates(p.year).join(', ')}.</p>}
          <details>
            <summary>Tax by bracket</summary>
            <table className="table">
              <thead><tr><th scope="col">Rate</th><th scope="col">Income in bracket</th><th scope="col">Tax</th></tr></thead>
              <tbody>{r.brackets.filter((b) => b.taxed > 0).map((b) => <tr key={b.rate}><td>{pct(b.rate)}</td><td>{usd2.format(b.taxed)}</td><td>{usd2.format(b.tax)}</td></tr>)}{r.taxableIncome <= 0 && <tr><td colSpan={3}>No taxable income.</td></tr>}</tbody>
            </table>
          </details>
        </section>
      </div>
    </div>
  )
}
