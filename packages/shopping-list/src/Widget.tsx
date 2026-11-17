import type { App } from '@modelcontextprotocol/ext-apps'
import { useApp, useHostStyles } from '@modelcontextprotocol/ext-apps/react'
import { useState } from 'react'
import { languageOf, messagesFor } from './messages'
import { WidgetView, type WidgetData } from './WidgetView'

/**
 * Talks to the host (Claude, ChatGPT…) through the MCP Apps bridge: receives the result of the
 * tool that opened the widget, and calls server tools back through the host, which adds the
 * person's access token. The widget itself never sees a token and makes no network request.
 */
export function Widget() {
  const [data, setData] = useState<WidgetData | null>(null)
  const [pingText, setPingText] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [failed, setFailed] = useState(false)

  const { app, isConnected, error } = useApp({
    appInfo: { name: 'kitchen-widget', version: '0.5.0' },
    capabilities: {},
    // Handlers must be set before the handshake: the host may send the result right after it.
    onAppCreated: (created: App) => {
      created.ontoolresult = (result) => setData(parseData(result.structuredContent))
    },
  })
  useHostStyles(app, app?.getHostContext())

  const locale = app?.getHostContext()?.locale ?? navigator.language
  const t = messagesFor(languageOf(locale))

  async function refresh() {
    if (!app) return
    setRefreshing(true)
    try {
      const result = await app.callServerTool({ name: 'ping', arguments: {} })
      const text = result.content.find((block) => block.type === 'text')
      setPingText(text && 'text' in text ? text.text : null)
      setData((current) => current && { ...current, servedAt: new Date().toISOString() })
    } catch {
      setFailed(true)
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <WidgetView
      t={t}
      locale={locale}
      connected={isConnected}
      error={Boolean(error) || failed}
      data={data}
      pingText={pingText}
      refreshing={refreshing}
      onRefresh={refresh}
    />
  )
}

/** Tool results come from the server but go through the host: check their shape. */
function parseData(value: unknown): WidgetData | null {
  if (typeof value !== 'object' || value === null) return null
  const { household, servedAt } = value as Record<string, unknown>
  if (typeof servedAt !== 'string') return null
  return { household: typeof household === 'string' ? household : null, servedAt }
}
