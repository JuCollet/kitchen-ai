import { fireEvent, render, screen, within } from '@testing-library/react'
import { listOf, withChecked, type ShoppingList } from './list'
import { languageOf, messagesFor } from './messages'
import { ShoppingListView } from './ShoppingListView'

const list: ShoppingList = {
  id: '01a1e8d4-0000-7000-8000-000000000001',
  title: 'Semaine du 23 novembre',
  remaining: 2,
  itemCount: 3,
  items: [
    { id: 'i1', name: 'poireaux', quantity: 3, unit: null, aisle: 'produce', checked: false },
    {
      id: 'i2',
      name: 'lait',
      quantity: 1.5,
      unit: 'l',
      aisle: 'dairy',
      checked: true,
      checkedBy: 'Alice',
    },
    { id: 'i3', name: 'œufs', quantity: 6, unit: null, aisle: 'dairy', checked: false },
  ],
}

const t = messagesFor('fr')

describe('ShoppingListView', () => {
  it('groups items by aisle, with translated aisle names and quantities in the locale', () => {
    render(<ShoppingListView list={list} t={t} locale="fr-BE" onToggle={() => {}} />)
    const dairy = screen.getByRole('heading', { name: 'Produits laitiers et œufs' }).parentElement!
    expect(within(dairy).getByText('lait')).toBeInTheDocument()
    expect(within(dairy).getByText('1,5 l')).toBeInTheDocument()
    expect(screen.getByText('2 articles restants')).toBeInTheDocument()
    expect(screen.getByText('coché par Alice')).toBeInTheDocument()
  })

  it('shows names written by the household as text, never as HTML', () => {
    const name = '<img src=x onerror=alert(1)>'
    const hostile = { ...list, items: [{ ...list.items[0], name }] }
    const { container } = render(
      <ShoppingListView list={hostile} t={t} locale="fr" onToggle={() => {}} />,
    )
    expect(screen.getByText(name)).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })

  it('reports a tap with the new state', () => {
    const onToggle = vi.fn()
    render(<ShoppingListView list={list} t={t} locale="fr" onToggle={onToggle} />)
    fireEvent.click(screen.getByRole('checkbox', { name: /poireaux/ }))
    expect(onToggle).toHaveBeenCalledWith(list.items[0], true)
  })
})

describe('withChecked', () => {
  it('updates the item and the remaining count at once', () => {
    const updated = withChecked(list, 'i1', true)
    expect(updated.items[0].checked).toBe(true)
    expect(updated.remaining).toBe(1)
    expect(withChecked(updated, 'i2', false).items[1].checkedBy).toBeNull()
  })
})

describe('listOf', () => {
  it('reads a tool result and refuses anything else', () => {
    expect(listOf({ list, pageUrl: 'https://kitchen.julco.dev/list/x' })?.title).toBe(list.title)
    expect(listOf({ household: 'x' })).toBeNull()
    expect(listOf(null)).toBeNull()
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
