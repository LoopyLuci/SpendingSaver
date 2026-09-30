import { useState, useEffect, useCallback } from 'react'

type Locale = 'en' | 'es' | 'fr'

const STORAGE_KEY = 'spendingsaver-locale'

async function loadTranslations(locale: Locale): Promise<Record<string, string>> {
  try {
    const res = await fetch(`/locales.json`)
    const all = await res.json()
    return all[locale] || all['en']
  } catch {
    return {}
  }
}

export function useI18n() {
  const [locale, setLocaleState] = useState<Locale>(() => {
    try {
      return (localStorage.getItem(STORAGE_KEY) as Locale) || 'en'
    } catch {
      return 'en'
    }
  })
  const [translations, setTranslations] = useState<Record<string, string>>({})

  useEffect(() => {
    loadTranslations(locale).then(setTranslations)
    localStorage.setItem(STORAGE_KEY, locale)
  }, [locale])

  const setLocale = useCallback((l: Locale) => setLocaleState(l), [])

  const t = useCallback((key: string): string => {
    return translations[key] || key
  }, [translations])

  return { locale, setLocale, t }
}
