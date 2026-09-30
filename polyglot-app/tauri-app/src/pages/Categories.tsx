import { useEffect, useState } from 'react'
import { listCategories, createCategory, updateCategory, deleteCategory } from '../hooks/useBackend'

export default function Categories() {
  const [categories, setCategories] = useState<any[]>([])
  const [name, setName] = useState('')
  const [color, setColor] = useState('#3b82f6')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [editColor, setEditColor] = useState('')

  useEffect(() => {
    listCategories()
      .then(setCategories)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await createCategory({ name, color })
      setName('')
      setColor('#3b82f6')
      const updated = await listCategories()
      setCategories(updated)
    } catch (e: any) {
      setError(e.message)
    }
  }

  function startEdit(cat: any) {
    setEditingId(cat.id)
    setEditName(cat.name)
    setEditColor(cat.color || '#3b82f6')
  }

  async function saveEdit(id: string) {
    try {
      await updateCategory(id, { name: editName, color: editColor })
      setEditingId(null)
      const updated = await listCategories()
      setCategories(updated)
    } catch (e: any) {
      setError(e.message)
    }
  }

  async function remove(id: string) {
    try {
      await deleteCategory(id)
      setCategories(prev => prev.filter(c => c.id !== id))
    } catch (e: any) {
      setError(e.message)
    }
  }

  return (
    <div className="space-y-6">
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Add Category</h3>
        {error && <div className="text-red-600 text-sm mb-3">{error}</div>}
        <form className="space-y-3" onSubmit={submit}>
          <div className="grid gap-3 md:grid-cols-2">
            <input value={name} onChange={e => setName(e.target.value)} placeholder="Name" className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
            <input value={color} onChange={e => setColor(e.target.value)} className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600" />
          </div>
          <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded">Save</button>
        </form>
      </section>
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">Categories</h3>
        {loading ? (
          <div className="text-sm text-gray-500">Loading...</div>
        ) : (
          <ul className="space-y-2">
            {categories.map(cat => (
              <li key={cat.id} className="flex items-center justify-between gap-3">
                {editingId === cat.id ? (
                  <div className="flex flex-1 gap-2">
                    <input value={editName} onChange={e => setEditName(e.target.value)} className="border rounded p-1 dark:bg-gray-700 dark:border-gray-600" />
                    <input value={editColor} onChange={e => setEditColor(e.target.value)} className="border rounded p-1 dark:bg-gray-700 dark:border-gray-600" />
                    <button onClick={() => saveEdit(cat.id)} className="px-2 py-1 bg-green-600 text-white rounded">Save</button>
                    <button onClick={() => setEditingId(null)} className="px-2 py-1 border rounded">Cancel</button>
                  </div>
                ) : (
                  <>
                    <div className="flex items-center gap-2">
                      <span className="w-3 h-3 rounded" style={{ backgroundColor: cat.color || '#999' }} />
                      <span>{cat.name}</span>
                    </div>
                    <div className="flex items-center gap-3">
                      <button onClick={() => startEdit(cat)} className="px-2 py-1 border rounded">Edit</button>
                      <button onClick={() => remove(cat.id)} className="px-2 py-1 border rounded text-red-600">Delete</button>
                    </div>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
