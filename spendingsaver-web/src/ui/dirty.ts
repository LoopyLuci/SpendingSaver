import { useEffect, useSyncExternalStore } from 'react'

/** Counts open forms with unsaved edits so an app update is never applied underneath them. */
let count = 0
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((l) => l())

export function useRegisterDirty(dirty: boolean) {
  useEffect(() => {
    if (!dirty) return
    count++
    emit()
    return () => { count--; emit() }
  }, [dirty])
}

export function useHasUnsavedEdits(): boolean {
  return useSyncExternalStore((l) => { listeners.add(l); return () => { listeners.delete(l) } }, () => count > 0)
}
