import { fireEvent, render, screen } from '@testing-library/react'
import { languageOf, messagesFor } from './messages'
import { WidgetView } from './WidgetView'

const base = {
  t: messagesFor('fr'),
  locale: 'fr-BE',
  connected: true,
  error: false,
  pingText: null,
  refreshing: false,
  onRefresh: () => {},
}

describe('WidgetView', () => {
  it('waits for the host before showing anything', () => {
    render(<WidgetView {...base} connected={false} data={null} />)
    expect(screen.getByText('Connexion à Kitchen…')).toBeInTheDocument()
  })

  it('shows the household as text, never as HTML', () => {
    const household = '<img src=x onerror=alert(1)>'
    const { container } = render(
      <WidgetView {...base} data={{ household, servedAt: '2026-11-17T08:00:00Z' }} />,
    )
    expect(screen.getByText(household)).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })

  it('refreshes through the host', () => {
    const onRefresh = vi.fn()
    render(
      <WidgetView
        {...base}
        onRefresh={onRefresh}
        data={{ household: null, servedAt: '2026-11-17T08:00:00Z' }}
      />,
    )
    expect(screen.getByText('Pas encore de foyer')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Rafraîchir' }))
    expect(onRefresh).toHaveBeenCalledOnce()
  })
})

describe('languageOf', () => {
  it('follows the host locale and falls back to English', () => {
    expect(languageOf('nl-BE')).toBe('nl')
    expect(languageOf('fr')).toBe('fr')
    expect(languageOf('de-DE')).toBe('en')
    expect(languageOf(undefined)).toBe('en')
  })
})
