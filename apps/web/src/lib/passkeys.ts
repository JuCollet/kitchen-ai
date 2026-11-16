import { z } from 'zod'
import { request } from './api'

/**
 * Passkeys through the browser's WebAuthn API, talking to Spring Security's endpoints. The JSON
 * helpers (parse…FromJSON, toJSON) are part of the WebAuthn level 3 API.
 */
export function passkeysSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.PublicKeyCredential !== 'undefined' &&
    'parseCreationOptionsFromJSON' in window.PublicKeyCredential &&
    'parseRequestOptionsFromJSON' in window.PublicKeyCredential
  )
}

const anyJson = z.unknown()

/** Adds a passkey to the signed-in account. */
export async function registerPasskey(label: string): Promise<void> {
  const options = await request('POST', '/webauthn/register/options', anyJson)
  const publicKey = PublicKeyCredential.parseCreationOptionsFromJSON(
    options as PublicKeyCredentialCreationOptionsJSON,
  )
  const credential = (await navigator.credentials.create({ publicKey })) as PublicKeyCredential
  await request('POST', '/webauthn/register', anyJson, {
    publicKey: { credential: credential.toJSON(), label },
  })
}

const signInResultSchema = z.object({ redirectUrl: z.string().optional() })

/**
 * Signs in with a passkey already registered on this device or in a password manager. Returns
 * where to go next: the AI authorization request that needed a sign-in, if any.
 */
export async function signInWithPasskey(): Promise<string | undefined> {
  const options = await request('POST', '/webauthn/authenticate/options', anyJson)
  const publicKey = PublicKeyCredential.parseRequestOptionsFromJSON(
    options as PublicKeyCredentialRequestOptionsJSON,
  )
  const credential = (await navigator.credentials.get({ publicKey })) as PublicKeyCredential
  const result = await request('POST', '/login/webauthn', signInResultSchema, credential.toJSON())
  return result.redirectUrl
}
