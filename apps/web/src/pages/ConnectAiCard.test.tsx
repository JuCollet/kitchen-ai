import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { ConnectAiCard } from './ConnectAiCard'

describe('ConnectAiCard', () => {
  const writeText = vi.fn<(text: string) => Promise<void>>()
  const open = vi.fn()

  beforeEach(async () => {
    await i18n.changeLanguage('en')
    writeText.mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    vi.stubGlobal('open', open)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    writeText.mockReset()
    open.mockReset()
  })

  it('links to Claude with Kitchen prefilled, in a new tab', () => {
    render(<ConnectAiCard />)
    const link = screen.getByRole('link', { name: 'Add to Claude' })
    const href = new URL(link.getAttribute('href')!)
    expect(href.hostname).toBe('claude.ai')
    expect(href.searchParams.get('connectorName')).toBe('Kitchen')
    expect(href.searchParams.get('connectorUrl')).toBe(`${window.location.origin}/mcp`)
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('copies the URL and opens ChatGPT or Gemini, which have no prefill link', async () => {
    render(<ConnectAiCard />)

    fireEvent.click(screen.getByRole('button', { name: 'Add to ChatGPT' }))
    expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/mcp`)
    expect(open).toHaveBeenCalledWith(
      'https://chatgpt.com/plugins',
      '_blank',
      'noopener,noreferrer',
    )

    fireEvent.click(screen.getByRole('button', { name: 'Add to Gemini' }))
    expect(open).toHaveBeenLastCalledWith(
      'https://gemini.google.com/app',
      '_blank',
      'noopener,noreferrer',
    )
    await waitFor(() => expect(screen.getByRole('button', { name: 'Copied' })).toBeInTheDocument())
  })

  it('copies the MCP URL from the link at the bottom', async () => {
    render(<ConnectAiCard />)
    fireEvent.click(screen.getByRole('button', { name: 'Copy MCP URL' }))
    expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/mcp`)
    expect(open).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Copied' })).toBeInTheDocument())
  })
})
