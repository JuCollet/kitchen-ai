import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { App } from './App'

describe('App', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('fr')
  })

  it('renders the home page in the current language', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: 'Kitchen' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Commencer' })).toBeInTheDocument()
  })
})
