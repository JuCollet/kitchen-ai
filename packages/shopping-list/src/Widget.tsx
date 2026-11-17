import type { App } from '@modelcontextprotocol/ext-apps'
import { useApp, useHostStyles } from '@modelcontextprotocol/ext-apps/react'
import { useEffect, useRef, useState } from 'react'
import { listOf, withChecked, type ShoppingItem, type ShoppingList } from './list'
import { languageOf, messagesFor } from './messages'
import { ShoppingListView } from './ShoppingListView'

/** How often the widget re-reads the list, to see what other members checked. */
const REFRESH_MS = 10_000

/**
 * Talks to the host (Claude, ChatGPT…) through the MCP Apps bridge: receives the result of
 * get_shopping_list, and calls check_items, uncheck_items and get_shopping_list back through the
 * host, which adds the person's access token. The widget never sees a token and makes no network
 * request of its own.
 */
export function Widget() {
  const [list, setList] = useState<ShoppingList | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  // Changes sent and not answered yet: a refresh must not overwrite what the person just tapped.
  const pending = useRef(0)

  const { app, isConnected, error } = useApp({
    appInfo: { name: 'kitchen-shopping-list', version: '0.9.0' },
    capabilities: {},
    // Handlers must be set before the handshake: the host may send the result right after it.
    onAppCreated: (created: App) => {
      created.ontoolresult = (result) => setList(listOf(result.structuredContent))
    },
  })
  useHostStyles(app, app?.getHostContext())

  const locale = app?.getHostContext()?.locale ?? navigator.language
  const t = messagesFor(languageOf(locale))
  const listId = list?.id

  useEffect(() => {
    if (!app || !listId) return
    const timer = setInterval(async () => {
      if (document.visibilityState !== 'visible' || pending.current > 0) return
      try {
        const result = await app.callServerTool({
          name: 'get_shopping_list',
          arguments: { listId },
        })
        const fresh = listOf(result.structuredContent)
        if (fresh && pending.current === 0) setList(fresh)
      } catch {
        // Offline for a moment in the store: the next refresh will catch up.
      }
    }, REFRESH_MS)
    return () => clearInterval(timer)
  }, [app, listId])

  async function toggle(item: ShoppingItem, checked: boolean) {
    if (!app || !list) return
    const before = list
    setList(withChecked(list, item.id, checked))
    setNotice(null)
    pending.current += 1
    try {
      const result = await app.callServerTool({
        name: checked ? 'check_items' : 'uncheck_items',
        arguments: { listId: list.id, itemIds: [item.id] },
      })
      const saved = listOf(result.structuredContent)
      if (result.isError || !saved) throw new Error('not saved')
      if (pending.current === 1) setList(saved)
    } catch {
      setList(before)
      setNotice(t.saveFailed)
    } finally {
      pending.current -= 1
    }
  }

  if (error) return <p className="ksl ksl-notice">{t.error}</p>
  if (!isConnected || !list) return <p className="ksl">{t.connecting}</p>
  return <ShoppingListView list={list} t={t} locale={locale} notice={notice} onToggle={toggle} />
}
