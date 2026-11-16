import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { AppRoutes } from './App'

describe('AppRoutes', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('fr')
  })

  it('renders the home page in the current language', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Kitchen' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Commencer' })).toHaveAttribute('href', '/sign-in')
  })

  it('offers Google sign-in', () => {
    render(
      <MemoryRouter initialEntries={['/sign-in']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Continuer avec Google' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/google',
    )
  })
})
