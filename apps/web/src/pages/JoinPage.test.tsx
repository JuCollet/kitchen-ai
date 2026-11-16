import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { JoinPage } from './JoinPage'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderJoin(hash: string) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/join', hash }]}>
      <JoinPage />
    </MemoryRouter>,
  )
}

describe('JoinPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(async () => {
    await i18n.changeLanguage('en')
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    fetchMock.mockReset()
    sessionStorage.clear()
  })

  it('asks to sign in first and keeps the invitation for after', async () => {
    fetchMock.mockResolvedValue(jsonResponse(401, {}))

    renderJoin('#invite-token')

    expect(await screen.findByRole('link', { name: 'Sign in to accept' })).toBeInTheDocument()
    expect(sessionStorage.getItem('kitchen.pendingInvitation')).toBe('invite-token')
  })

  it('offers to join when signed in without a household', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(200, {
        user: {
          id: '019a8f3e-0000-7000-8000-000000000001',
          email: 'nina@example.test',
          displayName: 'Nina',
          locale: 'en',
        },
        household: null,
      }),
    )

    renderJoin('#invite-token')

    expect(await screen.findByRole('button', { name: 'Join the household' })).toBeInTheDocument()
  })

  it('reports a link without token as invalid', () => {
    renderJoin('')

    expect(
      screen.getByText('This invitation is invalid, already used or expired.'),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
