export type ThemeSetting = 'system' | 'light' | 'dark'

export const ACCENTS: { id: string; label: string; light: string; dark: string }[] = [
  { id: 'teal', label: 'Teal', light: '#0f766e', dark: '#2dd4bf' },
  { id: 'blue', label: 'Blue', light: '#1d4ed8', dark: '#60a5fa' },
  { id: 'purple', label: 'Purple', light: '#7e22ce', dark: '#c084fc' },
  { id: 'rose', label: 'Rose', light: '#be123c', dark: '#fb7185' },
  { id: 'orange', label: 'Orange', light: '#b45309', dark: '#fb923c' },
  { id: 'green', label: 'Green', light: '#15803d', dark: '#4ade80' },
]

const THEME_COLORS = { light: '#f6f7f9', dark: '#0b1220' }

export function resolveTheme(theme: ThemeSetting): 'light' | 'dark' {
  if (theme === 'light' || theme === 'dark') return theme
  return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/** Applies theme and accent to <html>. `theme-init.js` does the same before first paint from a localStorage mirror. */
export function applyTheme(theme: ThemeSetting, accent: string) {
  const resolved = resolveTheme(theme)
  const root = document.documentElement
  root.setAttribute('data-theme', resolved)
  root.setAttribute('data-accent', ACCENTS.some((a) => a.id === accent) ? accent : 'teal')
  document.querySelectorAll('meta[name="theme-color"]').forEach((m) => {
    m.setAttribute('content', THEME_COLORS[resolved])
    if (theme !== 'system') m.removeAttribute('media')
  })
  try {
    window.localStorage.setItem('ss-theme', theme)
    window.localStorage.setItem('ss-accent', accent)
  } catch { /* storage blocked: the setting still applies for this session */ }
}
