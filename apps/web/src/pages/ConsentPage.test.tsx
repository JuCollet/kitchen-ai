import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { ConsentPage } from './ConsentPage'

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('ConsentPage', () => {
  const fetchMock = vi.fn<typeof fetch>()
  const submit = vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => {})

  beforeEach(async () => {
    await i18n.changeLanguage('en')
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=token; Path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    fetchMock.mockReset()
    submit.mockClear()
  })

  function serve(healthDataConsent: boolean) {
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.startsWith('/api/consent')) {
        return json({ clientName: 'Claude', redirectHosts: ['claude.ai'] })
      }
      if (url === '/api/me/health-data-consent') {
        const body = init?.body ? (JSON.parse(String(init.body)) as { consent: boolean }) : null
        return json({ consent: body ? body.consent : healthDataConsent })
      }
      return new Response(null, { status: 404 })
    })
  }

  function renderPage() {
    return render(
      <MemoryRouter initialEntries={['/consent?client_id=abc&state=xyz&scope=kitchen']}>
        <ConsentPage />
      </MemoryRouter>,
    )
  }

  it('shows the AI and where access goes, and posts the answer to the authorization server', async () => {
    serve(true)
    const { container } = renderPage()

    expect(
      await screen.findByText(/Claude asks to access your Kitchen household/),
    ).toBeInTheDocument()
    expect(screen.getByText('claude.ai')).toBeInTheDocument()
    const [allow, deny] = container.querySelectorAll('form')
    expect(allow).toHaveAttribute('action', '/oauth2/authorize')
    expect(allow?.querySelector('input[name="scope"]')).toHaveValue('kitchen')
    expect(deny?.querySelector('input[name="scope"]')).toBeNull()
    expect(
      await screen.findByText(/You allowed Kitchen to store your allergies/),
    ).toBeInTheDocument()
  })

  it('asks for health data consent unticked, and saves it before allowing the AI', async () => {
    serve(false)
    renderPage()

    const checkbox = await screen.findByRole('checkbox')
    expect(checkbox).not.toBeChecked()
    fireEvent.click(checkbox)
    fireEvent.click(screen.getByRole('button', { name: 'Allow' }))

    await waitFor(() => expect(submit).toHaveBeenCalledOnce())
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')
    expect(put?.[0]).toBe('/api/me/health-data-consent')
    expect(put?.[1]?.body).toBe(JSON.stringify({ consent: true }))
  })

  it('does not record consent when the box stays unticked', async () => {
    serve(false)
    renderPage()

    await screen.findByRole('checkbox')
    fireEvent.click(screen.getByRole('button', { name: 'Allow' }))

    await waitFor(() => expect(submit).toHaveBeenCalledOnce())
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false)
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
