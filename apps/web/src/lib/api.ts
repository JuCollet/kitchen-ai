import { z } from 'zod'

/** An API call that failed. `code` is the stable error code sent by the server, if any. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined

  constructor(status: number, code?: string) {
    super(`API error ${status}${code ? ` (${code})` : ''}`)
    this.status = status
    this.code = code
  }
}

const problemSchema = z.object({ code: z.string().optional() })

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

/**
 * CSRF protection: the server sets a XSRF-TOKEN cookie that only this site can read, and expects
 * it back in the X-XSRF-TOKEN header of every POST. The cookie is reset at sign-in, so it is
 * fetched again whenever it is missing.
 */
async function csrfToken(): Promise<string> {
  const existing = readCookie('XSRF-TOKEN')
  if (existing) return decodeURIComponent(existing)
  await fetch('/api/csrf', { credentials: 'same-origin' })
  const token = readCookie('XSRF-TOKEN')
  if (!token) throw new ApiError(0, 'csrf_unavailable')
  return decodeURIComponent(token)
}

async function send(method: string, path: string, body?: unknown): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = await csrfToken()
  return fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

/** Calls the API and validates the response with `schema` (data from the server is checked too). */
export async function request<T>(
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  schema: z.ZodType<T>,
  body?: unknown,
): Promise<T> {
  let response = await send(method, path, body)
  if (response.status === 403 && method !== 'GET') {
    // Stale CSRF cookie (for example after signing in): retry once with a fresh one.
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
    response = await send(method, path, body)
  }
  if (!response.ok) {
    const problem = problemSchema.safeParse(await response.json().catch(() => ({})))
    throw new ApiError(response.status, problem.success ? problem.data.code : undefined)
  }
  if (response.status === 204) return schema.parse(undefined)
  return schema.parse(await response.json())
}

const roleSchema = z.enum(['owner', 'member'])

export const householdSchema = z.object({ id: z.uuid(), name: z.string(), role: roleSchema })
export type Household = z.infer<typeof householdSchema>

export const meSchema = z.object({
  user: z.object({
    id: z.uuid(),
    email: z.string(),
    displayName: z.string(),
    locale: z.enum(['fr', 'nl', 'en']),
  }),
  household: householdSchema.nullable(),
})
export type Me = z.infer<typeof meSchema>

export const memberSchema = z.object({
  userId: z.uuid(),
  displayName: z.string(),
  role: roleSchema,
  joinedAt: z.string(),
})
export type Member = z.infer<typeof memberSchema>

export const invitationSchema = z.object({ token: z.string(), expiresAt: z.string() })
export type Invitation = z.infer<typeof invitationSchema>

export const healthDataConsentSchema = z.object({ consent: z.boolean() })

/** Form input, validated before it is sent (the server validates again). */
export const householdNameSchema = z.string().trim().min(1, 'required').max(100, 'tooLong')

export const api = {
  me: () => request('GET', '/api/me', meSchema),
  // async: invalid input rejects the promise, like a server error, instead of throwing.
  createHousehold: async (name: string) =>
    request('POST', '/api/households', householdSchema, { name: householdNameSchema.parse(name) }),
  members: () => request('GET', '/api/household/members', z.array(memberSchema)),
  createInvitation: () => request('POST', '/api/household/invitations', invitationSchema),
  acceptInvitation: (token: string) =>
    request('POST', '/api/invitations/accept', householdSchema, { token }),
  signOut: () => request('POST', '/logout', z.undefined()),
  healthDataConsent: () =>
    request('GET', '/api/me/health-data-consent', healthDataConsentSchema).then((r) => r.consent),
  setHealthDataConsent: (consent: boolean) =>
    request('PUT', '/api/me/health-data-consent', healthDataConsentSchema, { consent }).then(
      (r) => r.consent,
    ),
}
