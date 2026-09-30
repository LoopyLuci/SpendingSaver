import { useEffect, useState } from 'react'

export type Settings = {
  apiBase: string
  currency: string
  dateFormat: string
  autoStartBackends: boolean
  theme: 'system' | 'light' | 'dark'
  accent: string
  textScale: number
  showBalance: boolean
  compact: boolean
  telemetry: boolean
}

export const ACCENTS = ['teal', 'blue', 'indigo', 'green', 'purple', 'orange', 'rose', 'slate']

const DEFAULTS: Settings = {
  apiBase: 'http://127.0.0.1:8125',
  currency: 'USD',
  dateFormat: 'MM/dd/yyyy',
  autoStartBackends: true,
  theme: 'system',
  accent: 'teal',
  textScale: 1,
  showBalance: true,
  compact: false,
  telemetry: false,
}

const STORAGE_KEY = 'spendingsaver-settings'

// Hex accents for both the active `--accent` and each named variant.
const ACCENT_HEX: Record<string, string> = {
  teal: '#0d9488',
  blue: '#2563eb',
  indigo: '#4f46e5',
  green: '#16a34a',
  purple: '#9333ea',
  orange: '#ea580c',
  rose: '#e11d48',
  slate: '#475569',
}

export function useSettings() {
  const [settings, setSettings] = useState<Settings>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      return raw ? { ...DEFAULTS, ...JSON.parse(raw) } : DEFAULTS
    } catch {
      return DEFAULTS
    }
  })

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
    const root = document.documentElement.classList
    const prefersDark = typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
    const isDark = settings.theme === 'dark' || (settings.theme === 'system' && prefersDark)
    root.toggle('dark', isDark)

    // Apply active accent + text scale, and expose each accent for the picker.
    const style = document.documentElement.style
    style.setProperty('--accent', ACCENT_HEX[settings.accent] || ACCENT_HEX.teal)
    ACCENTS.forEach((a) => style.setProperty(`--accent-${a}`, ACCENT_HEX[a]))
    style.setProperty('--text-scale', String(settings.textScale))
    document.body.style.fontSize = `${settings.textScale * 16}px`
  }, [settings])

  return { settings, setSettings }
}