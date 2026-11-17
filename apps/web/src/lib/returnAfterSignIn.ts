/**
 * A page that needs a signed-in person (the shopping list opened from an AI app) is remembered for
 * the duration of the tab while the person signs in: Google sign-in is a full-page redirect that
 * always comes back to /account, which then sends the person back here.
 *
 * Only paths of this site that are known to be safe to return to are accepted, on the way in and
 * on the way out: the value lives in browser storage, so it is never trusted as is (no open
 * redirect to another site, no arbitrary path).
 */
const KEY = 'kitchen.returnAfterSignIn'
const ALLOWED = /^\/list\/[A-Za-z0-9-]{1,64}$/

export function saveReturnPath(path: string): void {
  if (!ALLOWED.test(path)) return
  try {
    sessionStorage.setItem(KEY, path)
  } catch {
    // Storage unavailable: the person lands on their account page and opens the link again.
  }
}

export function takeReturnPath(): string | null {
  try {
    const path = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)
    return path && ALLOWED.test(path) ? path : null
  } catch {
    return null
  }
}
