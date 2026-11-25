import { useEffect } from 'react'
import { navigate, useRoute } from './router'

/** Opens a dialog when the URL carries ?add=1 (used by the manifest shortcut and "Add" buttons), then cleans the URL. */
export function useAddParam(open: () => void) {
  const route = useRoute()
  const flag = route.query.get('add')
  useEffect(() => {
    if (flag) {
      open()
      navigate(route.path, true)
    }
  }, [flag])
}
