import { useEffect, useState } from 'react'

/** Hash routing ("#/transactions?add=1"): works from any sub-folder on static hosting and needs no server rewrites. */
export interface Route { path: string; query: URLSearchParams }

export function parseHash(hash: string): Route {
  const raw = hash.replace(/^#/, '') || '/'
  const [p, q = ''] = raw.split('?')
  const path = '/' + p.replace(/^\/+/, '').replace(/\/+$/, '')
  return { path, query: new URLSearchParams(q) }
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => parseHash(window.location.hash))
  useEffect(() => {
    const on = () => setRoute(parseHash(window.location.hash))
    window.addEventListener('hashchange', on)
    return () => window.removeEventListener('hashchange', on)
  }, [])
  return route
}

export function navigate(path: string, replace = false) {
  const target = `#${path}`
  if (replace) window.history.replaceState(null, '', target)
  else window.location.hash = target
  window.dispatchEvent(new HashChangeEvent('hashchange'))
}
