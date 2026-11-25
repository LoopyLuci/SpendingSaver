import { useState, type FormEvent } from 'react'
import { useSnapshot, useStore, useToast } from '../context'
import { newId } from '../../lib/id'
import { CATEGORY_IDEAS, COLORS, MAX_NAME, type Category } from '../../domain/types'
import { ColorPicker, ComboField, ConfirmDialog, Dot, EmptyState, Modal, PageHeader } from '../components'
import { deleteCategory } from '../../storage/actions'
import { useAddParam } from '../useAddParam'
import { Icon } from '../Icon'

function CategoryForm({ existing, onClose }: { existing?: Category; onClose: () => void }) {
  const store = useStore()
  const toast = useToast()
  const { live } = useSnapshot()
  const initial = { name: existing?.name ?? '', color: existing?.color ?? COLORS[live.categories.length % COLORS.length].value }
  const [f, setF] = useState(initial)
  const [error, setError] = useState<string | null>(null)
  const [confirm, setConfirm] = useState(false)
  const dirty = JSON.stringify(f) !== JSON.stringify(initial)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const name = f.name.trim()
    if (!name) return setError('Enter a category name.')
    if (name.length > MAX_NAME) return setError(`Keep the name under ${MAX_NAME} characters.`)
    if (live.categories.some((c) => c.id !== existing?.id && c.name.trim().toLowerCase() === name.toLowerCase())) return setError('A category with this name already exists.')
    await store.upsert('categories', { id: existing?.id ?? newId(), updatedAt: 0, deleted: false, name, color: f.color, parentId: existing?.parentId ?? null })
    toast.show(existing ? 'Category updated.' : 'Category added.')
    onClose()
  }
  const uses = existing ? live.transactions.filter((t) => t.categoryId === existing.id).length : 0

  return (
    <>
      <Modal title={existing ? 'Edit category' : 'Add category'} onClose={onClose} dirty={dirty}>
        <form onSubmit={submit} noValidate className="stack">
          <ComboField id="cat-name" label="Name" value={f.name} onChange={(e) => { setF({ ...f, name: e.target.value }); setError(null) }} options={CATEGORY_IDEAS.filter((i) => !live.categories.some((c) => c.name.toLowerCase() === i.toLowerCase()))} error={error} autoFocus hint="Pick a suggestion or type your own." />
          <ColorPicker value={f.color} onChange={(color) => setF({ ...f, color })} />
          <div className="row between gap wrap">
            {existing ? <button type="button" className="btn danger-ghost" onClick={() => setConfirm(true)}><Icon name="trash" size={18} /> Delete</button> : <span />}
            <span className="row gap"><button type="button" className="btn" onClick={onClose}>Cancel</button><button type="submit" className="btn primary">{existing ? 'Save changes' : 'Add category'}</button></span>
          </div>
        </form>
      </Modal>
      {confirm && existing && (
        <ConfirmDialog title="Delete category?" confirmLabel="Delete category" onCancel={() => setConfirm(false)}
          body={<>Delete <strong>{existing.name}</strong>? Its {uses} transaction{uses === 1 ? '' : 's'} become Uncategorized and its budgets are removed. Your transactions are kept.</>}
          onConfirm={async () => { await deleteCategory(store, existing.id); toast.show('Category deleted.', { action: { label: 'Undo', run: () => { void store.restoreRows('categories', existing.id) } } }); onClose() }} />
      )}
    </>
  )
}

export default function Categories() {
  const { live } = useSnapshot()
  const [editing, setEditing] = useState<Category | 'new' | null>(null)
  useAddParam(() => setEditing('new'))
  const counts = new Map<string, number>()
  for (const t of live.transactions) if (t.categoryId) counts.set(t.categoryId, (counts.get(t.categoryId) ?? 0) + 1)
  const sorted = [...live.categories].sort((a, b) => a.name.localeCompare(b.name))
  return (
    <div>
      <PageHeader title="Categories"><button type="button" className="btn primary" onClick={() => setEditing('new')}><Icon name="plus" size={18} /> Add category</button></PageHeader>
      {sorted.length === 0 ? (
        <div className="card"><EmptyState icon="tag" title="No categories" action={<button type="button" className="btn primary" onClick={() => setEditing('new')}>Add a category</button>}>Categories group your spending for budgets and charts.</EmptyState></div>
      ) : (
        <ul className="card list">
          {sorted.map((c) => (
            <li key={c.id}>
              <button type="button" className="list-row" onClick={() => setEditing(c)} aria-label={`Edit category ${c.name}`}>
                <Dot color={c.color} /><span className="grow"><strong>{c.name}</strong></span><span className="muted small">{counts.get(c.id) ?? 0} transactions</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {editing && <CategoryForm existing={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
