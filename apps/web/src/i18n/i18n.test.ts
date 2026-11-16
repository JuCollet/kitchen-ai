import { describe, expect, it } from 'vitest'
import { detectLanguage } from '.'
import en from './locales/en.json'
import fr from './locales/fr.json'
import nl from './locales/nl.json'

function keys(messages: object, prefix = ''): string[] {
  return Object.entries(messages).flatMap(([key, value]) =>
    typeof value === 'object' && value !== null
      ? keys(value, `${prefix}${key}.`)
      : [`${prefix}${key}`],
  )
}

describe('detectLanguage', () => {
  it('uses the first supported browser language', () => {
    expect(detectLanguage(['de-DE', 'nl-BE', 'fr'])).toBe('nl')
  })

  it('falls back to English', () => {
    expect(detectLanguage(['de', 'es'])).toBe('en')
    expect(detectLanguage([])).toBe('en')
  })
})

describe('locales', () => {
  it('have exactly the same keys in FR, NL and EN', () => {
    const reference = keys(en).sort()
    expect(keys(fr).sort()).toEqual(reference)
    expect(keys(nl).sort()).toEqual(reference)
  })
})
