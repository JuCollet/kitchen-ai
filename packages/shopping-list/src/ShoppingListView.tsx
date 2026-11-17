import { aisleName, type Messages } from './messages'
import { groupByAisle, type ShoppingItem, type ShoppingList } from './list'

type Props = {
  list: ShoppingList
  t: Messages
  locale: string
  /** Shown above the list, for example when a change could not be saved. */
  notice?: string | null
  onToggle: (item: ShoppingItem, checked: boolean) => void
}

/**
 * The shopping list itself, shared by the MCP App widget and the site's fallback page: pure
 * display, the caller does the talking. Every text from the household is rendered as text, never
 * as HTML (CLAUDE.md rule 6).
 */
export function ShoppingListView({ list, t, locale, notice, onToggle }: Props) {
  const number = new Intl.NumberFormat(locale, { maximumFractionDigits: 3 })
  return (
    <section className="ksl">
      <header className="ksl-header">
        <h1 className="ksl-title">{list.title}</h1>
        <p className="ksl-remaining">
          {list.remaining === 0 ? t.allDone : t.remaining(list.remaining)}
        </p>
      </header>
      {notice && (
        <p className="ksl-notice" role="alert">
          {notice}
        </p>
      )}
      {groupByAisle(list.items).map(([aisle, items]) => (
        <section key={aisle} className="ksl-aisle">
          <h2 className="ksl-aisle-name">{aisleName(t, aisle)}</h2>
          <ul className="ksl-items">
            {items.map((item) => (
              <li key={item.id} className={item.checked ? 'ksl-item ksl-checked' : 'ksl-item'}>
                <label className="ksl-label">
                  <input
                    type="checkbox"
                    className="ksl-checkbox"
                    checked={item.checked}
                    onChange={(event) => onToggle(item, event.target.checked)}
                  />
                  <span className="ksl-name">{item.name}</span>
                  {item.quantity != null && (
                    <span className="ksl-quantity">
                      {number.format(item.quantity)}
                      {item.unit ? ` ${item.unit}` : ''}
                    </span>
                  )}
                </label>
                {item.checked && item.checkedBy && (
                  <span className="ksl-by">{t.checkedBy(item.checkedBy)}</span>
                )}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </section>
  )
}
