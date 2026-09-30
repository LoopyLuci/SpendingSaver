import { useEffect, useState } from 'react'
import { listDebts, createDebt, addDebtPayment, getDebtPayoffStrategy } from '../hooks/useBackend'

export default function Debts() {
  const [debts, setDebts] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [strategy, setStrategy] = useState<string>('snowball')
  const [payoffResult, setPayoffResult] = useState<any>(null)
  const [form, setForm] = useState({ name: '', creditor: '', balance: '', interest_rate: '', minimum_payment: '', due_date: '', debt_type: 'credit_card' })
  const [paymentForms, setPaymentForms] = useState<Record<string, string>>({})

  useEffect(() => { load() }, [])

  const load = async () => {
    setLoading(true)
    try { setDebts(await listDebts()) } catch (e: any) { setError(e.message) } finally { setLoading(false) }
  }

  const addDebt = async () => {
    setError(null)
    try {
      await createDebt({ ...form, balance: parseFloat(form.balance || '0'), interest_rate: parseFloat(form.interest_rate || '0'), minimum_payment: parseFloat(form.minimum_payment || '0') })
      setForm({ name: '', creditor: '', balance: '', interest_rate: '', minimum_payment: '', due_date: '', debt_type: 'credit_card' })
      load()
    } catch (e: any) { setError(e.message) }
  }

  const makePayment = async (debtId: string) => {
    const amount = parseFloat(paymentForms[debtId] || '0')
    if (amount <= 0) return
    setError(null)
    try {
      await addDebtPayment(debtId, { amount })
      setPaymentForms({ ...paymentForms, [debtId]: '' })
      load()
    } catch (e: any) { setError(e.message) }
  }

  const calculate = async () => {
    try { setPayoffResult(await getDebtPayoffStrategy(strategy)) } catch (e: any) { setError(e.message) }
  }

  const totalDebt = debts.reduce((sum, d) => sum + d.balance, 0)
  const totalMinPayment = debts.reduce((sum, d) => sum + d.minimum_payment, 0)

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Debt Overview</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
          <div className="p-3 bg-red-50 dark:bg-red-900/20 rounded">
            <div className="text-sm text-gray-500">Total Debt</div>
            <div className="text-xl font-bold text-red-600">${totalDebt.toFixed(2)}</div>
          </div>
          <div className="p-3 bg-orange-50 dark:bg-orange-900/20 rounded">
            <div className="text-sm text-gray-500">Min. Payment</div>
            <div className="text-xl font-bold text-orange-600">${totalMinPayment.toFixed(2)}</div>
          </div>
          <div className="p-3 bg-blue-50 dark:bg-blue-900/20 rounded">
            <div className="text-sm text-gray-500">Accounts</div>
            <div className="text-xl font-bold text-blue-600">{debts.length}</div>
          </div>
          <div className="p-3 bg-green-50 dark:bg-green-900/20 rounded">
            <div className="text-sm text-gray-500">Avg. Rate</div>
            <div className="text-xl font-bold text-green-600">{debts.length ? (debts.reduce((s, d) => s + d.interest_rate, 0) / debts.length).toFixed(1) : 0}%</div>
          </div>
        </div>
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Payoff Calculator</h3>
        <div className="flex gap-3 mb-4">
          <select value={strategy} onChange={e => setStrategy(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600">
            <option value="snowball">Snowball (smallest first)</option>
            <option value="avalanche">Avalanche (highest rate first)</option>
          </select>
          <button onClick={calculate} className="px-4 py-2 bg-blue-600 text-white rounded">Calculate</button>
        </div>
        {payoffResult && (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-3 bg-gray-50 dark:bg-gray-700 rounded">
              <div className="text-sm text-gray-500">Payoff Months</div>
              <div className="text-xl font-bold">{payoffResult.payoff_months}</div>
            </div>
            <div className="p-3 bg-gray-50 dark:bg-gray-700 rounded">
              <div className="text-sm text-gray-500">Total Interest</div>
              <div className="text-xl font-bold">${payoffResult.total_interest.toFixed(2)}</div>
            </div>
            <div className="p-3 bg-gray-50 dark:bg-gray-700 rounded">
              <div className="text-sm text-gray-500">Total Paid</div>
              <div className="text-xl font-bold">${payoffResult.total_paid.toFixed(2)}</div>
            </div>
            <div className="p-3 bg-gray-50 dark:bg-gray-700 rounded">
              <div className="text-sm text-gray-500">Strategy</div>
              <div className="text-xl font-bold capitalize">{payoffResult.strategy}</div>
            </div>
          </div>
        )}
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Add Debt</h3>
        {error && <div className="text-red-600 text-sm mb-2">{error}</div>}
        <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
          <input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="Name" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.creditor} onChange={e => setForm({ ...form, creditor: e.target.value })} placeholder="Creditor" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.balance} onChange={e => setForm({ ...form, balance: e.target.value })} placeholder="Balance" type="number" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.interest_rate} onChange={e => setForm({ ...form, interest_rate: e.target.value })} placeholder="Interest Rate %" type="number" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <input value={form.minimum_payment} onChange={e => setForm({ ...form, minimum_payment: e.target.value })} placeholder="Min. Payment" type="number" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          <select value={form.debt_type} onChange={e => setForm({ ...form, debt_type: e.target.value })} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600">
            <option value="credit_card">Credit Card</option>
            <option value="loan">Loan</option>
            <option value="mortgage">Mortgage</option>
            <option value="student">Student Loan</option>
            <option value="other">Other</option>
          </select>
        </div>
        <button onClick={addDebt} className="mt-3 px-4 py-2 bg-blue-600 text-white rounded">Add Debt</button>
      </section>

      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Debts</h3>
        {loading && <p className="text-gray-500">Loading...</p>}
        {!loading && debts.length === 0 && <p className="text-gray-500">No debts yet.</p>}
        <ul className="divide-y dark:divide-gray-700">
          {debts.map(d => (
            <li key={d.id} className="py-3">
              <div className="flex items-center justify-between mb-2">
                <div>
                  <div className="font-medium">{d.name}</div>
                  <div className="text-sm text-gray-500">{d.creditor} • {d.debt_type} • {d.interest_rate}% APR</div>
                </div>
                <div className="text-right">
                  <div className="font-bold text-red-600">${d.balance.toFixed(2)}</div>
                  <div className="text-sm text-gray-500">Min: ${d.minimum_payment.toFixed(2)}/mo</div>
                </div>
              </div>
              <div className="flex gap-2">
                <input value={paymentForms[d.id] || ''} onChange={e => setPaymentForms({ ...paymentForms, [d.id]: e.target.value })} placeholder="Payment amount" type="number" className="border rounded p-1 text-sm dark:bg-gray-700 dark:border-gray-600 flex-1" />
                <button onClick={() => makePayment(d.id)} className="px-3 py-1 bg-green-600 text-white rounded text-sm">Pay</button>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
