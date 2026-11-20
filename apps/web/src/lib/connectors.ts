/**
 * Adding Kitchen to an AI app. Checked on 20 November 2026:
 * - Claude opens its "add custom connector" dialog with the name and URL prefilled from these
 *   query parameters. Not in Anthropic's official docs (used by other MCP providers): if it stops
 *   prefilling, the dialog still opens and the person pastes the URL.
 * - ChatGPT and Gemini have no prefill link: the URL is copied and their settings page opened.
 */

/** Kitchen's MCP endpoint: the site and the API share one domain. */
export function mcpUrl(origin: string = window.location.origin): string {
  return `${origin}/mcp`
}

export function claudeConnectorLink(url: string, name = 'Kitchen'): string {
  const params = new URLSearchParams({
    modal: 'add-custom-connector',
    connectorName: name,
    connectorUrl: url,
  })
  return `https://claude.ai/customize/connectors?${params}`
}

/** "Plugins" page, where "+" then "Add custom MCP server" takes the URL. */
export const CHATGPT_PLUGINS_URL = 'https://chatgpt.com/plugins'

/** Gemini web app: Settings, Connected apps, Add a custom app. */
export const GEMINI_URL = 'https://gemini.google.com/app'
