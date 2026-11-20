import { describe, expect, it } from 'vitest'
import { claudeConnectorLink, mcpUrl } from './connectors'

describe('connectors', () => {
  it('points to the MCP endpoint of the site itself', () => {
    expect(mcpUrl('https://kitchen.julco.dev')).toBe('https://kitchen.julco.dev/mcp')
  })

  it('prefills Claude with the name and an encoded URL that cannot leak into other parameters', () => {
    const link = new URL(claudeConnectorLink('https://example.test/mcp?a=1&b=2', 'A & B'))
    expect(link.origin + link.pathname).toBe('https://claude.ai/customize/connectors')
    expect(link.searchParams.get('modal')).toBe('add-custom-connector')
    expect(link.searchParams.get('connectorName')).toBe('A & B')
    expect(link.searchParams.get('connectorUrl')).toBe('https://example.test/mcp?a=1&b=2')
  })
})
