import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './api'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('api', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=token-123; Path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    fetchMock.mockReset()
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
  })

  it('sends the CSRF cookie back in a header on POST', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(201, {
        id: '019a8f3e-0000-7000-8000-000000000001',
        name: 'Home',
        role: 'owner',
      }),
    )

    await api.createHousehold('  Home  ')

    const [path, init] = fetchMock.mock.calls[0]!
    expect(path).toBe('/api/households')
    expect(init?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'token-123' })
    expect(init?.body).toBe(JSON.stringify({ name: 'Home' }))
  })

  it('rejects invalid input before calling the server', async () => {
    await expect(api.createHousehold('x'.repeat(101))).rejects.toThrow()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('exposes the error code sent by the server', async () => {
    fetchMock.mockResolvedValue(jsonResponse(409, { code: 'already_in_household' }))

    const error = await api.acceptInvitation('abc').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: 'already_in_household' })
  })

  it('validates responses from the server', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { user: { id: 'not-a-uuid' } }))

    await expect(api.me()).rejects.toThrow()
  })
})
