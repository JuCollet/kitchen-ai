import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { ConsentPage } from './ConsentPage'

describe('ConsentPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(async () => {
    await i18n.changeLanguage('en')
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    fetchMock.mockReset()
  })

  it('shows the AI and where access goes, and posts the answer to the authorization server', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ clientName: 'Claude', redirectHosts: ['claude.ai'] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const { container } = render(
      <MemoryRouter initialEntries={['/consent?client_id=abc&state=xyz&scope=kitchen']}>
        <ConsentPage />
      </MemoryRouter>,
    )

    expect(
      await screen.findByText(/Claude asks to access your Kitchen household/),
    ).toBeInTheDocument()
    expect(screen.getByText('claude.ai')).toBeInTheDocument()
    const [allow, deny] = container.querySelectorAll('form')
    expect(allow).toHaveAttribute('action', '/oauth2/authorize')
    expect(allow?.querySelector('input[name="scope"]')).toHaveValue('kitchen')
    expect(deny?.querySelector('input[name="scope"]')).toBeNull()
  })

  it('reports an incomplete request without calling the server', () => {
    render(
      <MemoryRouter initialEntries={['/consent']}>
        <ConsentPage />
      </MemoryRouter>,
    )

    expect(screen.getByText(/invalid or has expired/)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
