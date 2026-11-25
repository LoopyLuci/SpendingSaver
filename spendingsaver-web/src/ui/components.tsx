import { useEffect, useId, useRef, type ReactNode, type InputHTMLAttributes } from 'react'
import { COLORS } from '../domain/types'
import { useRegisterDirty } from './dirty'
import { Icon } from './Icon'

// ------------------------------------------------------------------ Modal (native <dialog>: focus trap + Esc for free)
export function Modal({ title, onClose, children, dirty = false, wide = false }: { title: string; onClose: () => void; children: ReactNode; dirty?: boolean; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useRegisterDirty(dirty)
  useEffect(() => {
    const d = ref.current
    if (d && !d.open) {
      if (typeof d.showModal === 'function') d.showModal()
      else d.setAttribute('open', '')
    }
  }, [])
  return (
    <dialog
      ref={ref}
      className={wide ? 'modal wide' : 'modal'}
      aria-labelledby={titleId}
      onCancel={(e) => { e.preventDefault(); onClose() }}
      onClick={(e) => { if (e.target === ref.current) onClose() }}
    >
      <div className="modal-inner">
        <div className="modal-head">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="btn icon-btn ghost" onClick={onClose} aria-label="Close"><Icon name="close" /></button>
        </div>
        {children}
      </div>
    </dialog>
  )
}

export function ConfirmDialog({ title, body, confirmLabel, onConfirm, onCancel, danger = true }: {
  title: string; body: ReactNode; confirmLabel: string; onConfirm: () => void; onCancel: () => void; danger?: boolean
}) {
  return (
    <Modal title={title} onClose={onCancel}>
      <div className="stack">
        <div>{body}</div>
        <div className="row end gap">
          <button type="button" className="btn" onClick={onCancel}>Cancel</button>
          <button type="button" className={danger ? 'btn danger' : 'btn primary'} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </Modal>
  )
}

// ------------------------------------------------------------------ Form fields
export function Field({ label, error, hint, children, htmlFor }: { label: string; error?: string | null; hint?: string; children: ReactNode; htmlFor: string }) {
  return (
    <div className="field">
      <label htmlFor={htmlFor}>{label}</label>
      {children}
      {hint && !error && <p className="hint" id={`${htmlFor}-hint`}>{hint}</p>}
      {error && <p className="error" id={`${htmlFor}-err`} role="alert">{error}</p>}
    </div>
  )
}

type InputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> & { id: string; label: string; error?: string | null; hint?: string }

export function TextField({ id, label, error, hint, ...rest }: InputProps) {
  return (
    <Field label={label} error={error} hint={hint} htmlFor={id}>
      <input id={id} {...rest} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-err` : hint ? `${id}-hint` : undefined} />
    </Field>
  )
}

/** Free-text input with a native suggestion list: pick a suggestion or type anything. */
export function ComboField({ id, label, options, error, hint, ...rest }: InputProps & { options: string[] }) {
  const listId = `${id}-list`
  return (
    <Field label={label} error={error} hint={hint} htmlFor={id}>
      <input id={id} list={listId} autoComplete="off" {...rest} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-err` : hint ? `${id}-hint` : undefined} />
      <datalist id={listId}>{options.map((o) => <option key={o} value={o} />)}</datalist>
    </Field>
  )
}

export function SelectField({ id, label, value, onChange, children, error, hint }: { id: string; label: string; value: string; onChange: (v: string) => void; children: ReactNode; error?: string | null; hint?: string }) {
  return (
    <Field label={label} error={error} hint={hint} htmlFor={id}>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-err` : undefined}>{children}</select>
    </Field>
  )
}

export function Segmented<T extends string>({ label, value, options, onChange }: { label: string; value: T; options: { value: T; label: string }[]; onChange: (v: T) => void }) {
  return (
    <div className="field">
      <span className="label" id={`seg-${label}`}>{label}</span>
      <div className="segmented" role="radiogroup" aria-labelledby={`seg-${label}`}>
        {options.map((o) => (
          <button key={o.value} type="button" role="radio" aria-checked={value === o.value} className={value === o.value ? 'seg on' : 'seg'} onClick={() => onChange(o.value)}>{o.label}</button>
        ))}
      </div>
    </div>
  )
}

export function ColorPicker({ value, onChange, label = 'Colour' }: { value: string | null; onChange: (v: string) => void; label?: string }) {
  return (
    <div className="field">
      <span className="label" id="color-label">{label}</span>
      <div className="swatches" role="radiogroup" aria-labelledby="color-label">
        {COLORS.map((c) => (
          <button key={c.value} type="button" role="radio" aria-checked={value?.toLowerCase() === c.value} aria-label={c.name} title={c.name}
            className={value?.toLowerCase() === c.value ? 'swatch on' : 'swatch'} onClick={() => onChange(c.value)}>
            <span className="swatch-dot" data-color={c.value} ref={(el) => { if (el) el.style.background = c.value }} />
          </button>
        ))}
      </div>
    </div>
  )
}

// ------------------------------------------------------------------ small pieces
export function EmptyState({ icon, title, children, action }: { icon: string; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty-icon"><Icon name={icon} size={30} /></div>
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  )
}

export function ProgressBar({ pct, label, over, color }: { pct: number; label: string; over?: boolean; color?: string }) {
  const clamped = Math.max(0, Math.min(100, pct))
  return (
    <div className={over ? 'progress over' : 'progress'} role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.min(pct, 100)} aria-valuetext={`${pct}%`}>
      <span ref={(el) => { if (el) { el.style.width = `${clamped}%`; if (color && !over) el.style.background = color } }} />
    </div>
  )
}

export function Dot({ color }: { color: string | null | undefined }) {
  return <span className="dot" aria-hidden="true" ref={(el) => { if (el) el.style.background = color || '#9e9e9e' }} />
}

export function PageHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <header className="page-head">
      <h1 tabIndex={-1} id="page-title">{title}</h1>
      <div className="row gap wrap">{children}</div>
    </header>
  )
}

export function ErrorList({ errors }: { errors: string[] }) {
  if (!errors.length) return null
  return <ul className="error-list" role="alert">{errors.map((e) => <li key={e}>{e}</li>)}</ul>
}
