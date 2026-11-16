import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { api, ApiError } from '@/lib/api'
import { savePendingInvitation } from '@/lib/pendingInvitation'
import { PageShell } from './PageShell'

type State = 'loading' | 'signedOut' | 'ready' | 'notFound' | 'alreadyInHousehold' | 'error'

/** Invitation link: /join#<token>. The token stays in the fragment, never sent in a URL. */
export function JoinPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const token = useLocation().hash.slice(1)
  const [state, setState] = useState<State>(token ? 'loading' : 'notFound')

  useEffect(() => {
    if (!token) return
    api.me().then(
      (me) => setState(me.household ? 'alreadyInHousehold' : 'ready'),
      (error: unknown) => {
        if (error instanceof ApiError && error.status === 401) {
          // Kept while the person signs in with Google, then picked up by the account page.
          savePendingInvitation(token)
          setState('signedOut')
        } else {
          setState('error')
        }
      },
    )
  }, [token])

  async function accept() {
    try {
      await api.acceptInvitation(token)
      navigate('/account')
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) setState('notFound')
      else if (error instanceof ApiError && error.code === 'already_in_household')
        setState('alreadyInHousehold')
      else setState('error')
    }
  }

  return (
    <PageShell title={t('join.title')}>
      {state === 'loading' && <p>{t('common.loading')}</p>}
      {(state === 'signedOut' || state === 'ready') && <p>{t('join.intro')}</p>}
      {state === 'signedOut' && (
        <Button asChild>
          <Link to="/sign-in">{t('join.signInFirst')}</Link>
        </Button>
      )}
      {state === 'ready' && <Button onClick={() => void accept()}>{t('join.accept')}</Button>}
      {state === 'notFound' && <Message text={t('join.notFound')} />}
      {state === 'alreadyInHousehold' && <Message text={t('join.alreadyInHousehold')} />}
      {state === 'error' && <Message text={t('common.error')} />}
    </PageShell>
  )
}

function Message({ text }: { text: string }) {
  return (
    <Alert variant="destructive">
      <AlertDescription>{text}</AlertDescription>
    </Alert>
  )
}
