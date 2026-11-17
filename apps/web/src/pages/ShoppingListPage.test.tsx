import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { ShoppingListPage } from './ShoppingListPage'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const list = {
  id: 'list-1',
  title: 'Week',
  remaining: 1,
  itemCount: 1,
  items: [{ id: 'i1', name: 'milk', quantity: 2, unit: 'l', aisle: 'dairy', checked: false }],
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/list/list-1']}>
        <Routes>
          <Route path="/list/:listId" element={<ShoppingListPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ShoppingListPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(async () => {
    await i18n.changeLanguage('en')
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=test'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    fetchMock.mockReset()
  })

  it('shows the list and checks an item through the site API', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, list))
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { ...list, remaining: 0, items: [{ ...list.items[0], checked: true }] }),
    )

    renderPage()
    fireEvent.click(await screen.findByRole('checkbox', { name: /milk/ }))

    expect(await screen.findByText('Everything is bought.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/shopping-lists/list-1/check',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ itemIds: ['i1'] }) }),
    )
  })

  it('puts the box back and says so when the change is not saved', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, list))
    fetchMock.mockResolvedValueOnce(jsonResponse(500, {}))

    renderPage()
    const box = await screen.findByRole('checkbox', { name: /milk/ })
    fireEvent.click(box)

    expect(await screen.findByRole('alert')).toHaveTextContent('That change was not saved.')
    await waitFor(() => expect(box).not.toBeChecked())
  })

  it('says when the list does not exist or belongs to another household', async () => {
    fetchMock.mockResolvedValue(jsonResponse(404, { code: 'not_found' }))

    renderPage()

    expect(
      await screen.findByText(/does not exist, or belongs to another household/),
    ).toBeInTheDocument()
  })

  it('asks to sign in and remembers the list for after', async () => {
    fetchMock.mockResolvedValue(jsonResponse(401, {}))

    renderPage()

    expect(await screen.findByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/sign-in')
    expect(sessionStorage.getItem('kitchen.returnAfterSignIn')).toBe('/list/list-1')
  })
})
