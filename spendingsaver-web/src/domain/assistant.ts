import { formatMoney, percent } from '../lib/money'
import { periodWindow, type Period, type Ymd } from '../lib/dates'
import { budgetStatus, inCurrency } from './stats'
import { monthlyEquivalentCents } from './recurring'
import { goalMonthlyNeedCents, payoffSchedule } from './payoff'
import type { Dataset } from './types'

/**
 * The offline assistant: a deterministic, rule-based question answerer. Pure functions only: no network,
 * no model, no storage access. It reads the snapshot it is given and returns plain text.
 */
export const SUGGESTIONS = [
  'What is my balance?',
  'How much did I spend this month?',
  'Top spending categories',
  'How are my budgets?',
  'Biggest expenses',
  'Recent transactions',
  'What are my recurring bills?',
  'How are my goals and debts?',
]

export interface AssistantSnapshot {
  data: Dataset
  currency: string
  today: Ymd
}

type Win = 'week' | 'month' | 'year' | 'all'

const LABEL: Record<Win, string> = { week: 'this week', month: 'this month', year: 'this year', all: 'overall' }

const has = (q: string, words: string[]) => words.some((w) => q.includes(w))

function windowFor(q: string): Win {
  if (has(q, ['all time', 'overall', 'ever'])) return 'all'
  if (q.includes('week')) return 'week'
  if (q.includes('year')) return 'year'
  return 'month'
}

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

const STOP = new Set(['much', 'spend', 'spent', 'this', 'month', 'week', 'year', 'what', 'have', 'money', 'show', 'tell', 'about', 'with', 'from', 'did', 'how', 'many'])

export function answer(question: string, s: AssistantSnapshot): string {
  const q = question.toLowerCase().trim()
  if (!q || has(q, ['help', 'what can you'])) return help()
  const m = (c: number) => formatMoney(c, s.currency)
  const d = s.data
  const txs = inCurrency(d.transactions, d.accounts, s.currency)
  const w = windowFor(q)
  const inWin = (date: Ymd) => {
    if (w === 'all') return true
    const { from, to } = periodWindow(w === 'week' ? 'weekly' : w === 'year' ? 'yearly' : 'monthly', s.today)
    return date >= from && date <= to
  }
  const expenses = () => txs.filter((t) => t.amountCents < 0 && inWin(t.date))
  const catName = (id: string | null) => d.categories.find((c) => c.id === id)?.name ?? 'Uncategorized'

  if (has(q, ['recurring', 'subscription', 'bills'])) {
    const act = d.recurring.filter((r) => r.active)
    if (!act.length) return 'You have no recurring items yet. Add them on the Recurring page.'
    const total = act.reduce((x, r) => x + monthlyEquivalentCents(r), 0)
    return `Recurring items, about ${m(total)} a month in total:\n` + act.map((r) => `• ${r.name} (${r.frequency}): ${m(r.amountCents)}, next ${r.nextDate}`).join('\n')
  }
  if (has(q, ['goal', 'saving for'])) {
    if (!d.goals.length) return 'You have no savings goals yet. Add one on the Goals page.'
    return 'Savings goals:\n' + d.goals.map((g) => {
      const need = goalMonthlyNeedCents(g, s.today)
      return `• ${g.name}: ${m(g.savedCents)} of ${m(g.targetCents)} (${percent(g.savedCents, g.targetCents)}%)${need != null ? `, ${m(need)} a month needed` : ''}`
    }).join('\n')
  }
  if (has(q, ['debt', 'loan', 'owe', 'payoff', 'pay off'])) {
    if (!d.debts.length) return 'You have no debts recorded. Add them on the Debts page.'
    const total = d.debts.reduce((x, y) => x + y.balanceCents, 0)
    return `You owe ${m(total)} in total:\n` + d.debts.map((x) => {
      const p = payoffSchedule(x.balanceCents, x.apr, x.minPaymentCents)
      const when = !p ? 'the payment does not cover the interest' : p.months === 0 ? 'paid off' : `paid off in about ${p.months} months at ${m(x.minPaymentCents)}/month`
      return `• ${x.name}: ${m(x.balanceCents)} at ${x.apr.toFixed(1)}% APR, ${when}`
    }).join('\n')
  }
  if (q.includes('budget')) {
    if (!d.budgets.length) return 'You have no budgets yet. Create one on the Budgets page.'
    return 'Budget status:\n' + d.budgets.map((b) => {
      const st = budgetStatus(b, txs, s.today)
      const state = st.over ? `over by ${m(-st.remaining)}` : `${m(st.remaining)} left`
      return `• ${catName(b.categoryId)} (${b.period}): ${m(st.spent)} of ${m(b.amountCents)}, ${state}`
    }).join('\n')
  }
  if (has(q, ['balance', 'net worth', 'how much do i have', 'total money'])) {
    const acc = d.accounts.filter((a) => a.currency.toUpperCase() === s.currency.toUpperCase())
    if (!acc.length) return 'You have no accounts yet. Add one on the Accounts page.'
    const total = acc.reduce((x, a) => x + a.balanceCents, 0)
    const sorted = [...acc].sort((a, b) => b.balanceCents - a.balanceCents)
    return `Your total balance is ${m(total)} across ${plural(acc.length, 'account', 'accounts')}:\n` + sorted.map((a) => `• ${a.name}: ${m(a.balanceCents)}`).join('\n')
  }
  if (has(q, ['categories', 'by category', 'top categor', 'where does my money', 'where did my money'])) {
    const ex = expenses()
    if (!ex.length) return `No expenses recorded ${LABEL[w]}.`
    const total = ex.reduce((x, t) => x - t.amountCents, 0)
    const rows = new Map<string, number>()
    for (const t of ex) rows.set(catName(t.categoryId), (rows.get(catName(t.categoryId)) ?? 0) - t.amountCents)
    const top = [...rows.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5)
    return `Top spending ${LABEL[w]} (total ${m(total)}):\n` + top.map(([n, c]) => `• ${n}: ${m(c)} (${percent(c, total)}%)`).join('\n')
  }
  if (has(q, ['biggest', 'largest', 'most expensive'])) {
    const ex = expenses().sort((a, b) => a.amountCents - b.amountCents).slice(0, 5)
    if (!ex.length) return `No expenses recorded ${LABEL[w]}.`
    return `Biggest expenses ${LABEL[w]}:\n` + ex.map((t) => `• ${t.description}: ${m(-t.amountCents)} on ${t.date}`).join('\n')
  }
  if (has(q, ['recent', 'latest', 'last transactions'])) {
    const l = [...d.transactions].sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : b.createdAt - a.createdAt)).slice(0, 5)
    if (!l.length) return 'No transactions yet.'
    return 'Most recent transactions:\n' + l.map((t) => `• ${t.date} ${t.description}: ${m(t.amountCents)}`).join('\n')
  }
  if (has(q, ['income', 'earn', 'salary'])) {
    const inc = txs.filter((t) => t.amountCents > 0 && inWin(t.date))
    if (!inc.length) return `No income recorded ${LABEL[w]}.`
    return `You received ${m(inc.reduce((x, t) => x + t.amountCents, 0))} ${LABEL[w]} in ${plural(inc.length, 'deposit', 'deposits')}.`
  }
  if (has(q, ['saving', 'saved'])) {
    const win = txs.filter((t) => inWin(t.date))
    const inc = win.filter((t) => t.amountCents > 0).reduce((x, t) => x + t.amountCents, 0)
    const out = win.filter((t) => t.amountCents < 0).reduce((x, t) => x - t.amountCents, 0)
    if (!inc && !out) return `Nothing recorded ${LABEL[w]}.`
    const rate = inc > 0 ? ` Savings rate: ${Math.trunc(((inc - out) / inc) * 100)}%.` : ''
    return `${LABEL[w][0].toUpperCase()}${LABEL[w].slice(1)}: income ${m(inc)}, spending ${m(out)}, net ${m(inc - out)}.${rate}`
  }
  if (q.includes('how many') && q.includes('transaction')) {
    return `You have ${plural(d.transactions.length, 'transaction', 'transactions')} across ${plural(d.accounts.length, 'account', 'accounts')}.`
  }
  if (has(q, ['spend', 'spent', 'expense', 'cost'])) {
    const named = byName(q, s, w, inWin, m)
    if (named) return named
    const ex = expenses()
    if (!ex.length) return `No expenses recorded ${LABEL[w]}.`
    return `You spent ${m(ex.reduce((x, t) => x - t.amountCents, 0))} ${LABEL[w]} in ${plural(ex.length, 'transaction', 'transactions')}.`
  }
  return byName(q, s, w, inWin, m) ?? `I did not understand that. ${help()}`
}

function byName(q: string, s: AssistantSnapshot, w: Win, inWin: (d: Ymd) => boolean, m: (c: number) => string): string | null {
  const txs = inCurrency(s.data.transactions, s.data.accounts, s.currency)
  const ex = txs.filter((t) => t.amountCents < 0 && inWin(t.date))
  const cats = s.data.categories.filter((c) => q.includes(c.name.toLowerCase())).sort((a, b) => b.name.length - a.name.length)
  const cat = cats[0]
  if (cat) {
    const hit = ex.filter((t) => t.categoryId === cat.id)
    return hit.length
      ? `You spent ${m(hit.reduce((x, t) => x - t.amountCents, 0))} on ${cat.name} ${LABEL[w]} (${plural(hit.length, 'transaction', 'transactions')}).`
      : `Nothing spent on ${cat.name} ${LABEL[w]}.`
  }
  const word = q.split(/[^a-z0-9]+/).filter((x) => x.length >= 4 && !STOP.has(x))
    .find((x) => s.data.transactions.some((t) => t.description.toLowerCase().includes(x)))
  if (!word) return null
  const hit = ex.filter((t) => t.description.toLowerCase().includes(word))
  return hit.length
    ? `You spent ${m(hit.reduce((x, t) => x - t.amountCents, 0))} on "${word}" ${LABEL[w]} (${plural(hit.length, 'transaction', 'transactions')}).`
    : `No spending matching "${word}" ${LABEL[w]}.`
}

function help(): string {
  return `I run entirely in this browser and only look at your own data. Try: ${SUGGESTIONS.map((x) => `"${x}"`).join('; ')}. You can also ask "how much did I spend on groceries" and add "this week" or "this year".`
}

export type { Period }
