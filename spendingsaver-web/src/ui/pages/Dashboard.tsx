import { useMemo, useState } from 'react'
import { useBaseCurrency, useSnapshot, useStore, useToday, useToast } from '../context'
import { formatMoney, percent } from '../../lib/money'
import { formatDate, periodWindow } from '../../lib/dates'
import { budgetStatus, currenciesInUse, inCurrency, monthlyTrend, netWorthCents, spendingByCategory, totalsBetween } from '../../domain/stats'
import { Dot, EmptyState, PageHeader, ProgressBar } from '../components'
import { TrendChart } from '../Charts'
import { loadSampleData } from '../../storage/actions'
import { Icon } from '../Icon'

export default function Dashboard() {
  const { live } = useSnapshot()
  const store = useStore()
  const toast = useToast()
  const today = useToday()
  const currency = useBaseCurrency()
  const [busy, setBusy] = useState(false)

  const model = useMemo(() => {
    const txs = inCurrency(live.transactions, live.accounts, currency)
    const month = periodWindow('monthly', today)
    const totals = totalsBetween(txs, month.from, month.to)
    return {
      txs,
      net: netWorthCents(live.accounts, currency),
      totals,
      byCat: spendingByCategory(txs, live.categories, month.from, month.to),
      trend: monthlyTrend(txs, today, 6),
      budgets: live.budgets.map((b) => budgetStatus(b, txs, today)).sort((a, b) => b.pct - a.pct).slice(0, 4),
      recent: [...txs].sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : b.createdAt - a.createdAt)).slice(0, 6),
      others: currenciesInUse(live.accounts).filter((c) => c !== currency),
    }
  }, [live, today, currency])

  if (live.accounts.length === 0) {
    return (
      <div>
        <PageHeader title="Dashboard" />
        <div className="card">
          <EmptyState icon="wallet" title="Welcome to SpendingSaver"
            action={
              <div className="row gap wrap center">
                <a className="btn primary" href="#/accounts?add=1">Add your first account</a>
                <button type="button" className="btn" disabled={busy} onClick={() => { setBusy(true); loadSampleData(store).then(() => toast.show('Sample data added. Delete it any time from Import / Export.')).finally(() => setBusy(false)) }}>Try with sample data</button>
              </div>
            }>
            Track accounts, spending and budgets. Everything stays on this device in your browser: no account, no server. You can also restore a backup from the Android or desktop app on the Import / Export page.
          </EmptyState>
        </div>
      </div>
    )
  }

  const catTotal = model.byCat.reduce((s, c) => s + c.cents, 0)
  const cur = (c: number) => formatMoney(c, currency)
  const net = model.totals.income - model.totals.expenses
  const accName = (id: string) => live.accounts.find((a) => a.id === id)?.name ?? 'Unknown account'

  return (
    <div>
      <PageHeader title="Dashboard"><a className="btn primary" href="#/transactions?add=1"><Icon name="plus" size={18} /> Add transaction</a></PageHeader>
      <section aria-label="Summary" className="stats">
        <div className="stat"><span className="stat-label">Net worth</span><strong className={model.net < 0 ? 'neg' : ''}>{cur(model.net)}</strong></div>
        <div className="stat"><span className="stat-label">Income this month</span><strong className="pos">{cur(model.totals.income)}</strong></div>
        <div className="stat"><span className="stat-label">Spent this month</span><strong>{cur(model.totals.expenses)}</strong></div>
        <div className="stat"><span className="stat-label">Net this month</span><strong className={net < 0 ? 'neg' : 'pos'}>{cur(net)}</strong></div>
      </section>
      {model.others.length > 0 && (
        <p className="note">Totals show {currency} accounts only. You also have accounts in {model.others.join(', ')}; amounts in different currencies are never added together or converted.</p>
      )}
      <div className="grid-2">
        <section className="card" aria-labelledby="h-cat">
          <h2 id="h-cat">Spending by category <span className="muted small">this month</span></h2>
          {model.byCat.length === 0 ? <p className="muted">No spending recorded this month.</p> : (
            <ul className="bars">
              {model.byCat.slice(0, 7).map((c) => (
                <li key={c.categoryId ?? 'none'}>
                  <div className="row between"><span className="row gap center"><Dot color={c.color} />{c.name}</span><span><strong>{cur(c.cents)}</strong> <span className="muted small">{percent(c.cents, catTotal)}%</span></span></div>
                  <ProgressBar pct={percent(c.cents, catTotal)} label={`${c.name} share of spending`} color={c.color} />
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className="card" aria-labelledby="h-trend">
          <h2 id="h-trend">Last 6 months</h2>
          <TrendChart points={model.trend} currency={currency} />
        </section>
        <section className="card" aria-labelledby="h-bud">
          <div className="row between center"><h2 id="h-bud">Budgets</h2><a className="link-tap" href="#/budgets">All budgets</a></div>
          {model.budgets.length === 0 ? <p className="muted">No budgets yet. <a href="#/budgets?add=1">Create one</a> to see progress here.</p> : (
            <ul className="bars">
              {model.budgets.map((b) => {
                const name = live.categories.find((c) => c.id === b.budget.categoryId)?.name ?? 'Unknown'
                return (
                  <li key={b.budget.id}>
                    <div className="row between"><span>{name} <span className="muted small">{b.budget.period}</span></span><span className={b.over ? 'neg' : ''}>{cur(b.spent)} of {cur(b.limit)}</span></div>
                    <ProgressBar pct={b.pct} over={b.over} label={`${name} budget used`} />
                  </li>
                )
              })}
            </ul>
          )}
        </section>
        <section className="card" aria-labelledby="h-recent">
          <div className="row between center"><h2 id="h-recent">Recent transactions</h2><a className="link-tap" href="#/transactions">See all</a></div>
          {model.recent.length === 0 ? <p className="muted">No transactions yet.</p> : (
            <ul className="list">
              {model.recent.map((t) => (
                <li key={t.id} className="list-row static">
                  <span className="grow"><strong>{t.description}</strong><span className="muted small block">{formatDate(t.date)} · {accName(t.accountId)}</span></span>
                  <span className={t.amountCents < 0 ? 'amt' : 'amt pos'}>{cur(t.amountCents)}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
