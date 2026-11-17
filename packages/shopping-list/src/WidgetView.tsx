import type { Messages } from './messages'

export type WidgetData = { household: string | null; servedAt: string }

type Props = {
  t: Messages
  locale: string
  connected: boolean
  error: boolean
  data: WidgetData | null
  pingText: string | null
  refreshing: boolean
  onRefresh: () => void
}

/** Pure display, tested without a host. */
export function WidgetView({
  t,
  locale,
  connected,
  error,
  data,
  pingText,
  refreshing,
  onRefresh,
}: Props) {
  if (error) return <p className="message danger">{t.error}</p>
  if (!connected || !data) return <p className="message">{t.connecting}</p>

  const time = new Date(data.servedAt).toLocaleTimeString(locale)
  return (
    <section className="card">
      <h1>{t.title}</h1>
      <dl>
        <dt>{t.household}</dt>
        {/* Rendered as text: React escapes it, never inserted as HTML (CLAUDE.md rule 6). */}
        <dd>{data.household ?? t.noHousehold}</dd>
        <dt>{t.servedAt}</dt>
        <dd>{time}</dd>
      </dl>
      {pingText && (
        <p className="ping">
          {t.lastPing}: {pingText}
        </p>
      )}
      <button type="button" onClick={onRefresh} disabled={refreshing}>
        {refreshing ? t.refreshing : t.refresh}
      </button>
    </section>
  )
}
