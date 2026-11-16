/**
 * The invitation token is kept for the duration of the tab while the person signs in with Google
 * (a full-page redirect). Browser storage can be unavailable: everything still works, the person
 * just has to open the link again.
 */
const KEY = 'kitchen.pendingInvitation'

export function savePendingInvitation(token: string): void {
  try {
    sessionStorage.setItem(KEY, token)
  } catch {
    // Storage unavailable: nothing to do.
  }
}

export function takePendingInvitation(): string | null {
  try {
    const token = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)
    return token
  } catch {
    return null
  }
}
