import { describe, expect, it } from 'vitest'
import { answer } from './assistant'
import { emptyDataset, type Dataset } from './types'
import { acct, bud, cat, tx } from './helpers'

// Same scenario as the desktop app's assistant tests (assistant.rs), so both give the same answers.
function data(): Dataset {
  const d = emptyDataset()
  d.accounts = [acct({ id: 'a1', name: 'Checking', balanceCents: 150000 }), acct({ id: 'a2', name: 'Savings', balanceCents: 100000 })]
  d.categories = [cat('Groceries', { id: 'c1' }), cat('Fun', { id: 'c2' })]
  d.transactions = [
    tx({ amountCents: -5000, description: 'Trader Joes', categoryId: 'c1', date: '2025-06-10' }),
    tx({ amountCents: -3000, description: 'Cinema night', categoryId: 'c2', date: '2025-06-11' }),
    tx({ amountCents: 200000, description: 'Paycheck', date: '2025-06-01' }),
    tx({ amountCents: -99900, description: 'Old thing', categoryId: 'c2', date: '2024-01-01' }),
  ]
  d.budgets = [bud({ categoryId: 'c1', amountCents: 20000 })]
  return d
}
const ask = (q: string, d = data()) => answer(q, { data: d, currency: 'USD', today: '2025-06-15' })

describe('offline assistant', () => {
  it('answers balance and spending questions', () => {
    expect(ask('What is my balance?')).toContain('$2,500.00')
    expect(ask('how much did I spend this month')).toContain('$80.00')
    expect(ask('how much did I spend on groceries')).toContain('$50.00')
    expect(ask('how much on cinema')).toContain('$30.00')
  })
  it('respects time windows, budgets and income', () => {
    expect(ask('top spending categories')).toContain('Groceries')
    expect(ask('top categories this month')).not.toContain('$999')
    expect(ask('how much did I spend overall')).toContain('$1,079.00')
    expect(ask('how are my budgets')).toContain('$150.00 left')
    expect(ask('income this month')).toContain('$2,000.00')
    expect(ask('biggest expenses this year')).toContain('Trader Joes')
    expect(ask('recent transactions')).toContain('Cinema night')
    expect(ask('how many transactions do I have')).toContain('4 transactions across 2 accounts')
  })
  it('flags an overspent budget', () => {
    const d = data()
    d.budgets = [bud({ categoryId: 'c1', amountCents: 4000 })]
    expect(ask('budgets', d)).toContain('over by $10.00')
  })
  it('covers recurring, goals and debts, and handles empty states and unknown questions', () => {
    const d = data()
    d.goals = [{ id: 'g', name: 'Trip', targetCents: 100000, savedCents: 25000, due: null, deleted: false, updatedAt: 1 }]
    d.debts = [{ id: 'd', name: 'Card', balanceCents: 100000, apr: 0, minPaymentCents: 10000, deleted: false, updatedAt: 1 }]
    d.recurring = [{ id: 'r', name: 'Rent', amountCents: -90000, accountId: 'a1', categoryId: null, frequency: 'monthly', nextDate: '2025-07-01', active: true, deleted: false, updatedAt: 1 }]
    expect(ask('my goals', d)).toContain('25%')
    expect(ask('how much debt', d)).toContain('10 months')
    expect(ask('my recurring bills', d)).toContain('Rent')
    expect(ask('my goals')).toContain('no savings goals')
    expect(ask('zzzz qqqq')).toContain('did not understand')
    expect(ask('')).toContain('in this browser')
    expect(answer('balance', { data: emptyDataset(), currency: 'USD', today: '2025-06-15' })).toContain('no accounts')
  })
  it('never touches the network', () => {
    const calls: unknown[] = []
    const g = globalThis as unknown as { fetch?: unknown }
    const orig = g.fetch
    g.fetch = (...a: unknown[]) => { calls.push(a); throw new Error('network') }
    try { ask('how much did I spend this month') } finally { g.fetch = orig }
    expect(calls).toEqual([])
  })
})
