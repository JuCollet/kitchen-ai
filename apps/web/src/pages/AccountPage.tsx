import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  api,
  ApiError,
  householdNameSchema,
  type Invitation,
  type Me,
  type Member,
} from '@/lib/api'
import { passkeysSupported, registerPasskey } from '@/lib/passkeys'
import { takePendingInvitation } from '@/lib/pendingInvitation'
import { PageShell } from './PageShell'

export function AccountPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [me, setMe] = useState<Me | null>(null)
  const [failed, setFailed] = useState(false)
  // Incremented to load the account again (after creating a household).
  const [version, setVersion] = useState(0)

  useEffect(() => {
    api.me().then(
      (loaded) => {
        // Back from Google sign-in with an invitation to accept.
        const pending = loaded.household ? null : takePendingInvitation()
        if (pending) navigate(`/join#${pending}`)
        else setMe(loaded)
      },
      (error: unknown) => {
        if (error instanceof ApiError && error.status === 401) navigate('/sign-in')
        else setFailed(true)
      },
    )
  }, [navigate, version])

  const [signOutFailed, setSignOutFailed] = useState(false)

  // Never pretend: if the server did not end the session, the person must know they are still
  // signed in.
  async function signOut() {
    try {
      await api.signOut()
      navigate('/')
    } catch {
      setSignOutFailed(true)
    }
  }

  if (failed) return <ErrorPage />
  if (!me) return <PageShell title={t('account.title')}>{t('common.loading')}</PageShell>

  return (
    <PageShell title={t('account.title')}>
      <p className="text-sm text-muted-foreground">
        {t('account.signedInAs', { name: me.user.displayName, email: me.user.email })}
      </p>
      {me.household ? (
        <HouseholdCard name={me.household.name} />
      ) : (
        <CreateHouseholdCard onCreated={() => setVersion((current) => current + 1)} />
      )}
      <HealthDataCard />
      <PasskeyCard />
      <Button variant="outline" onClick={() => void signOut()}>
        {t('account.signOut')}
      </Button>
      {signOutFailed && (
        <Alert variant="destructive">
          <AlertDescription>{t('account.signOutFailed')}</AlertDescription>
        </Alert>
      )}
    </PageShell>
  )
}

/** Consent for health data: given or withdrawn here, never by an AI. Withdrawing erases them. */
function HealthDataCard() {
  const { t } = useTranslation()
  const [consent, setConsent] = useState<boolean | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    api.healthDataConsent().then(setConsent, () => setFailed(true))
  }, [])

  async function change(value: boolean) {
    try {
      setConsent(await api.setHealthDataConsent(value))
      setFailed(false)
    } catch {
      setFailed(true)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('account.healthDataTitle')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {consent !== null && (
          <>
            <p className="text-sm">
              {t(consent ? 'account.healthDataOn' : 'account.healthDataOff')}
            </p>
            <Button variant="outline" onClick={() => void change(!consent)}>
              {t(consent ? 'account.withdrawHealthData' : 'account.allowHealthData')}
            </Button>
          </>
        )}
        {failed && (
          <Alert variant="destructive">
            <AlertDescription>{t('common.error')}</AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}

function ErrorPage() {
  const { t } = useTranslation()
  return (
    <PageShell title={t('account.title')}>
      <Alert variant="destructive">
        <AlertDescription>{t('common.error')}</AlertDescription>
      </Alert>
    </PageShell>
  )
}

function CreateHouseholdCard({ onCreated }: { onCreated: () => void }) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const parsed = householdNameSchema.safeParse(name)
    if (!parsed.success) {
      setValidationError(parsed.error.issues[0]?.message ?? 'required')
      return
    }
    setValidationError(null)
    try {
      await api.createHousehold(parsed.data)
      onCreated()
    } catch {
      setFailed(true)
    }
  }

  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <p>{t('account.noHousehold')}</p>
        <form className="flex flex-col gap-2" onSubmit={(event) => void submit(event)}>
          <Label htmlFor="household-name">{t('account.householdName')}</Label>
          <Input
            id="household-name"
            value={name}
            maxLength={100}
            aria-invalid={validationError !== null}
            onChange={(event) => setName(event.target.value)}
          />
          {validationError && (
            <p className="text-sm text-destructive">{t(`validation.${validationError}`)}</p>
          )}
          <Button type="submit">{t('account.createHousehold')}</Button>
        </form>
        {failed && (
          <Alert variant="destructive">
            <AlertDescription>{t('common.error')}</AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}

function HouseholdCard({ name }: { name: string }) {
  const { t, i18n } = useTranslation()
  const [members, setMembers] = useState<Member[]>([])
  const [invitation, setInvitation] = useState<Invitation | null>(null)
  const [copied, setCopied] = useState(false)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    api.members().then(setMembers, () => setFailed(true))
  }, [])

  async function invite() {
    try {
      setInvitation(await api.createInvitation())
      setCopied(false)
    } catch {
      setFailed(true)
    }
  }

  // The token is in the fragment (#): browsers never send it to any server, so it cannot end
  // up in access logs.
  const link = invitation ? `${window.location.origin}/join#${invitation.token}` : null

  async function copy() {
    if (!link) return
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('account.household', { name })}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div>
          <h2 className="mb-1 text-sm font-medium">{t('account.members')}</h2>
          <ul className="text-sm">
            {members.map((member) => (
              <li key={member.userId}>
                {member.displayName}{' '}
                <span className="text-muted-foreground">({t(`account.roles.${member.role}`)})</span>
              </li>
            ))}
          </ul>
        </div>
        <Button variant="outline" onClick={() => void invite()}>
          {t('account.invite')}
        </Button>
        {invitation && link && (
          <div className="flex flex-col gap-2">
            <p className="text-sm">
              {t('account.inviteLink', {
                date: new Date(invitation.expiresAt).toLocaleDateString(i18n.language),
              })}
            </p>
            <div className="flex gap-2">
              <Input readOnly value={link} aria-label={t('account.invite')} />
              <Button onClick={() => void copy()}>
                {copied ? t('account.copied') : t('account.copy')}
              </Button>
            </div>
          </div>
        )}
        {failed && (
          <Alert variant="destructive">
            <AlertDescription>{t('common.error')}</AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}

function PasskeyCard() {
  const { t } = useTranslation()
  const [status, setStatus] = useState<'idle' | 'added' | 'failed'>('idle')

  async function add() {
    try {
      await registerPasskey(`Kitchen ${new Date().toISOString().slice(0, 10)}`)
      setStatus('added')
    } catch {
      setStatus('failed')
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('account.passkeyTitle')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <p className="text-sm text-muted-foreground">{t('account.passkeyIntro')}</p>
        {passkeysSupported() ? (
          <Button variant="outline" onClick={() => void add()}>
            {t('account.addPasskey')}
          </Button>
        ) : (
          <p className="text-sm">{t('account.passkeyUnsupported')}</p>
        )}
        {status === 'added' && <p className="text-sm">{t('account.passkeyAdded')}</p>}
        {status === 'failed' && (
          <Alert variant="destructive">
            <AlertDescription>{t('account.passkeyFailed')}</AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}
