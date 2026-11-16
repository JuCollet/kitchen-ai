import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './locales/en.json'
import fr from './locales/fr.json'
import nl from './locales/nl.json'

export const supportedLanguages = ['fr', 'nl', 'en'] as const
export type Language = (typeof supportedLanguages)[number]

// Every visible string exists in FR, NL and EN (CLAUDE.md rule 10).
// The English file is the reference shape: the other locales must match it.
type Messages = typeof en
const resources: Record<Language, { translation: Messages }> = {
  en: { translation: en },
  fr: { translation: fr },
  nl: { translation: nl },
}

/** Picks the first supported language from the browser preferences, English otherwise. */
export function detectLanguage(preferences: readonly string[]): Language {
  for (const preference of preferences) {
    const base = preference.toLowerCase().split('-')[0]
    const match = supportedLanguages.find((language) => language === base)
    if (match) return match
  }
  return 'en'
}

void i18n.use(initReactI18next).init({
  resources,
  lng: detectLanguage(navigator.languages),
  fallbackLng: 'en',
  // React already escapes rendered values.
  interpolation: { escapeValue: false },
})

export default i18n
