// The widget is a few kilobytes of UI inside someone else's chat: a plain dictionary is enough,
// no i18next. The language comes from the host (BCP 47 locale), English by default. The site's
// fallback page uses the same messages for the list itself.

const messages = {
  en: {
    connecting: 'Connecting to Kitchen…',
    error: 'Kitchen could not be reached.',
    saveFailed: 'That change was not saved. Try again.',
    remaining: (count: number) => (count === 1 ? '1 item left' : `${count} items left`),
    allDone: 'Everything is bought.',
    checkedBy: (name: string) => `checked by ${name}`,
    aisles: {
      produce: 'Fruit and vegetables',
      frozen: 'Frozen',
      meat_fish: 'Meat and fish',
      dairy: 'Dairy and eggs',
      bakery: 'Bakery',
      dry_goods: 'Dry goods',
      canned: 'Canned goods',
      condiments: 'Condiments and spices',
      drinks: 'Drinks',
      household: 'Household',
      other: 'Other',
    },
  },
  fr: {
    connecting: 'Connexion à Kitchen…',
    error: 'Impossible de joindre Kitchen.',
    saveFailed: "La modification n'a pas été enregistrée. Réessayez.",
    remaining: (count: number) =>
      count === 1 ? '1 article restant' : `${count} articles restants`,
    allDone: 'Tout est acheté.',
    checkedBy: (name: string) => `coché par ${name}`,
    aisles: {
      produce: 'Fruits et légumes',
      frozen: 'Surgelés',
      meat_fish: 'Viande et poisson',
      dairy: 'Produits laitiers et œufs',
      bakery: 'Boulangerie',
      dry_goods: 'Épicerie sèche',
      canned: 'Conserves',
      condiments: 'Condiments et épices',
      drinks: 'Boissons',
      household: 'Entretien',
      other: 'Autres',
    },
  },
  nl: {
    connecting: 'Verbinden met Kitchen…',
    error: 'Kitchen is niet bereikbaar.',
    saveFailed: 'Die wijziging is niet bewaard. Probeer opnieuw.',
    remaining: (count: number) => (count === 1 ? 'nog 1 artikel' : `nog ${count} artikelen`),
    allDone: 'Alles is gekocht.',
    checkedBy: (name: string) => `afgevinkt door ${name}`,
    aisles: {
      produce: 'Groenten en fruit',
      frozen: 'Diepvries',
      meat_fish: 'Vlees en vis',
      dairy: 'Zuivel en eieren',
      bakery: 'Bakkerij',
      dry_goods: 'Droge voeding',
      canned: 'Conserven',
      condiments: 'Sauzen en kruiden',
      drinks: 'Dranken',
      household: 'Huishouden',
      other: 'Overige',
    },
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

export function aisleName(t: Messages, aisle: string): string {
  return aisle in t.aisles ? t.aisles[aisle as keyof Messages['aisles']] : t.aisles.other
}
