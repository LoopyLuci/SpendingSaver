import { useEffect, useRef, useState } from 'react'
import { formatCompact, formatMoney } from '../lib/money'
import { formatMonth } from '../lib/dates'
import type { MonthPoint } from '../domain/stats'

function useWidth<T extends HTMLElement>(): [React.RefObject<T>, number] {
  const ref = useRef<T>(null)
  const [w, setW] = useState(320)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const set = () => setW(Math.max(220, Math.round(el.getBoundingClientRect().width)))
    set()
    if (typeof ResizeObserver === 'undefined') { window.addEventListener('resize', set); return () => window.removeEventListener('resize', set) }
    const ro = new ResizeObserver(set)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])
  return [ref, w]
}

function niceMax(v: number): number {
  if (v <= 0) return 100
  const exp = Math.pow(10, Math.floor(Math.log10(v)))
  for (const m of [1, 2, 2.5, 5, 10]) if (m * exp >= v) return m * exp
  return 10 * exp
}

/** Income vs expenses per month as plain SVG. A hidden table below carries the same numbers for screen readers. */
export function TrendChart({ points, currency }: { points: MonthPoint[]; currency: string }) {
  const [ref, width] = useWidth<HTMLDivElement>()
  const height = 220
  const m = { top: 12, right: 8, bottom: 28, left: 46 }
  const innerW = width - m.left - m.right
  const innerH = height - m.top - m.bottom
  const max = niceMax(Math.max(...points.map((p) => Math.max(p.income, p.expenses)), 1))
  const y = (v: number) => m.top + innerH - (v / max) * innerH
  const band = innerW / points.length
  const barW = Math.max(6, Math.min(28, band / 2 - 4))
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(max * f))
  const summary = points.map((p) => `${formatMonth(p.key, 'long')}: income ${formatMoney(p.income, currency)}, expenses ${formatMoney(p.expenses, currency)}`).join('. ')
  return (
    <figure className="chart" ref={ref}>
      <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`Income and expenses for the last ${points.length} months. ${summary}`}>
        {ticks.map((t) => (
          <g key={t}>
            <line className="grid" x1={m.left} x2={width - m.right} y1={y(t)} y2={y(t)} />
            <text className="axis" x={m.left - 6} y={y(t) + 4} textAnchor="end">{formatCompact(t, currency)}</text>
          </g>
        ))}
        {points.map((p, i) => {
          const cx = m.left + band * i + band / 2
          return (
            <g key={p.key}>
              <rect className="bar-income" x={cx - barW - 1} y={y(p.income)} width={barW} height={Math.max(0, m.top + innerH - y(p.income))} rx="3"><title>{`${formatMonth(p.key, 'long')} income ${formatMoney(p.income, currency)}`}</title></rect>
              <rect className="bar-expense" x={cx + 1} y={y(p.expenses)} width={barW} height={Math.max(0, m.top + innerH - y(p.expenses))} rx="3"><title>{`${formatMonth(p.key, 'long')} expenses ${formatMoney(p.expenses, currency)}`}</title></rect>
              <text className="axis" x={cx} y={height - 8} textAnchor="middle">{formatMonth(p.key)}</text>
            </g>
          )
        })}
      </svg>
      <figcaption className="legend">
        <span><i className="sw income" aria-hidden="true" /> Income</span>
        <span><i className="sw expense" aria-hidden="true" /> Expenses</span>
      </figcaption>
      <table className="sr-only">
        <caption>Income and expenses by month</caption>
        <thead><tr><th scope="col">Month</th><th scope="col">Income</th><th scope="col">Expenses</th></tr></thead>
        <tbody>{points.map((p) => <tr key={p.key}><th scope="row">{formatMonth(p.key, 'long')}</th><td>{formatMoney(p.income, currency)}</td><td>{formatMoney(p.expenses, currency)}</td></tr>)}</tbody>
      </table>
    </figure>
  )
}
