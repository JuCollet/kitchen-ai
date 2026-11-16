import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { passkeysSupported, signInWithPasskey } from '@/lib/passkeys'
import { PageShell } from './PageShell'

/** Only follow redirects to this site's OAuth endpoints, never to another site. */
function isSameOriginServerPath(url: string): boolean {
  const target = new URL(url, window.location.origin)
  return target.origin === window.location.origin && target.pathname.startsWith('/oauth2/')
}

export function SignInPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [passkeyFailed, setPasskeyFailed] = useState(false)

  async function passkeySignIn() {
    setPasskeyFailed(false)
    try {
      const redirectUrl = await signInWithPasskey()
      // Resume an AI authorization request (a server URL, full navigation), else the account page.
      if (redirectUrl && isSameOriginServerPath(redirectUrl)) window.location.assign(redirectUrl)
      else navigate('/account')
    } catch {
      setPasskeyFailed(true)
    }
  }

  return (
    <PageShell title={t('signIn.title')}>
      <p className="text-muted-foreground">{t('signIn.intro')}</p>
      {searchParams.has('error') && (
        <Alert variant="destructive">
          <AlertDescription>{t('signIn.failed')}</AlertDescription>
        </Alert>
      )}
      {/* A full-page navigation: Spring Security redirects to Google, then back to /account. */}
      <Button asChild>
        <a href="/oauth2/authorization/google">{t('signIn.google')}</a>
      </Button>
      {passkeysSupported() && (
        <Button variant="outline" onClick={() => void passkeySignIn()}>
          {t('signIn.passkey')}
        </Button>
      )}
      {passkeyFailed && (
        <Alert variant="destructive">
          <AlertDescription>{t('signIn.passkeyFailed')}</AlertDescription>
        </Alert>
      )}
    </PageShell>
  )
}
