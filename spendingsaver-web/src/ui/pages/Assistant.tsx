import { useRef, useState, type FormEvent } from 'react'
import { useBaseCurrency, useSnapshot, useToday } from '../context'
import { SUGGESTIONS, answer } from '../../domain/assistant'
import { PageHeader } from '../components'

interface Msg { id: number; q: string; a: string }

export default function Assistant() {
  const { live } = useSnapshot()
  const today = useToday()
  const currency = useBaseCurrency()
  const [msgs, setMsgs] = useState<Msg[]>([])
  const [q, setQ] = useState('')
  const seq = useRef(0)
  const end = useRef<HTMLDivElement>(null)

  function ask(text: string) {
    const t = text.trim().slice(0, 300)
    if (!t) return
    setMsgs((m) => [...m, { id: ++seq.current, q: t, a: answer(t, { data: live, currency, today }) }])
    setQ('')
    requestAnimationFrame(() => end.current?.scrollIntoView({ block: 'nearest' }))
  }

  return (
    <div>
      <PageHeader title="Assistant" />
      <p className="muted">Ask about your balances, spending and budgets. It is a simple rule-based helper that runs in this page using only your data: no AI model, no network, nothing leaves your device.</p>
      <div className="chips" role="group" aria-label="Suggested questions">
        {SUGGESTIONS.map((s) => <button key={s} type="button" className="chip" onClick={() => ask(s)}>{s}</button>)}
      </div>
      <div className="card chat" role="log" aria-live="polite" aria-label="Conversation">
        {msgs.length === 0 && <p className="muted">Pick a suggestion or type a question below.</p>}
        {msgs.map((m) => (
          <div key={m.id} className="msg-pair">
            <p className="msg me"><span className="sr-only">You asked: </span>{m.q}</p>
            <p className="msg bot"><span className="sr-only">Answer: </span>{m.a}</p>
          </div>
        ))}
        <div ref={end} />
      </div>
      <form className="row gap ask" onSubmit={(e: FormEvent) => { e.preventDefault(); ask(q) }}>
        <label htmlFor="as-q" className="sr-only">Your question</label>
        <input id="as-q" value={q} onChange={(e) => setQ(e.target.value)} placeholder="e.g. How much did I spend on food this month?" maxLength={300} autoComplete="off" />
        <button type="submit" className="btn primary">Ask</button>
      </form>
    </div>
  )
}
