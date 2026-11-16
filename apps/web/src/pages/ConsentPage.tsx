import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { request } from '@/lib/api'
import { PageShell } from './PageShell'

const consentSchema = z.object({ clientName: z.string(), redirectHosts: z.array(z.string()) })
type Consent = z.infer<typeof consentSchema>

/**
 * Consent page of the OAuth flow: Spring Security sends the person here with client_id, state and
 * scope. The answer is a real form POST to /oauth2/authorize (not fetch): the response is a
 * redirect back to the AI, which the browser must follow. Submitting without scope means "deny".
 */
export function ConsentPage() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const clientId = searchParams.get('client_id') ?? ''
  const state = searchParams.get('state') ?? ''
  const scopes = (searchParams.get('scope') ?? '').split(' ').filter(Boolean)
  const valid = clientId !== '' && state !== ''
  const [consent, setConsent] = useState<Consent | null>(null)
  const [failed, setFailed] = useState(!valid)

  useEffect(() => {
    if (!valid) return
    request('GET', `/api/consent?client_id=${encodeURIComponent(clientId)}`, consentSchema).then(
      setConsent,
      () => setFailed(true),
    )
  }, [clientId, valid])

  if (failed) {
    return (
      <PageShell title={t('consent.title')}>
        <Alert variant="destructive">
          <AlertDescription>{t('consent.notFound')}</AlertDescription>
        </Alert>
      </PageShell>
    )
  }
  if (!consent) return <PageShell title={t('consent.title')}>{t('common.loading')}</PageShell>

  return (
    <PageShell title={t('consent.title')}>
      {/* Client name chosen by whoever registered the client: rendered as text, never as HTML. */}
      <p>{t('consent.intro', { client: consent.clientName })}</p>
      <div>
        <p className="text-sm text-muted-foreground">{t('consent.destination')}</p>
        <ul className="font-medium">
          {consent.redirectHosts.map((host) => (
            <li key={host}>{host}</li>
          ))}
        </ul>
      </div>
      <p className="text-sm">{t('consent.warning')}</p>
      <div className="flex gap-2">
        <form method="post" action="/oauth2/authorize">
          <input type="hidden" name="client_id" value={clientId} />
          <input type="hidden" name="state" value={state} />
          {scopes.map((scope) => (
            <input key={scope} type="hidden" name="scope" value={scope} />
          ))}
          <Button type="submit">{t('consent.allow')}</Button>
        </form>
        <form method="post" action="/oauth2/authorize">
          <input type="hidden" name="client_id" value={clientId} />
          <input type="hidden" name="state" value={state} />
          <Button type="submit" variant="outline">
            {t('consent.deny')}
          </Button>
        </form>
      </div>
    </PageShell>
  )
}
