// The widget is a few kilobytes of UI inside someone else's chat: a plain dictionary is enough,
// no i18next. The language comes from the host (BCP 47 locale), English by default.

const messages = {
  en: {
    title: 'Kitchen',
    connecting: 'Connecting to Kitchen…',
    household: 'Household',
    noHousehold: 'No household yet',
    servedAt: 'Served at',
    refresh: 'Refresh',
    refreshing: 'Refreshing…',
    lastPing: 'Server says',
    error: 'Kitchen could not be reached.',
  },
  fr: {
    title: 'Kitchen',
    connecting: 'Connexion à Kitchen…',
    household: 'Foyer',
    noHousehold: 'Pas encore de foyer',
    servedAt: 'Servi à',
    refresh: 'Rafraîchir',
    refreshing: 'Rafraîchissement…',
    lastPing: 'Le serveur répond',
    error: 'Impossible de joindre Kitchen.',
  },
  nl: {
    title: 'Kitchen',
    connecting: 'Verbinden met Kitchen…',
    household: 'Huishouden',
    noHousehold: 'Nog geen huishouden',
    servedAt: 'Geserveerd om',
    refresh: 'Vernieuwen',
    refreshing: 'Vernieuwen…',
    lastPing: 'De server antwoordt',
    error: 'Kitchen is niet bereikbaar.',
  },
} as const

export type Language = keyof typeof messages
export type Messages = (typeof messages)[Language]

export function languageOf(locale: string | undefined): Language {
  const language = locale?.slice(0, 2).toLowerCase()
  return language === 'fr' || language === 'nl' ? language : 'en'
}

export function messagesFor(language: Language): Messages {
  return messages[language]
}
