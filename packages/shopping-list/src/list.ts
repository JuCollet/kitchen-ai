import { z } from 'zod'

// The shopping list as the server sends it (ShoppingListView in the API), checked on arrival:
// it goes through the AI host or the network before reaching this code.
export const shoppingItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  quantity: z.number().nullish(),
  unit: z.string().nullish(),
  aisle: z.string(),
  checked: z.boolean(),
  checkedBy: z.string().nullish(),
  checkedAt: z.string().nullish(),
})

export const shoppingListSchema = z.object({
  id: z.string(),
  title: z.string(),
  remaining: z.number(),
  itemCount: z.number(),
  items: z.array(shoppingItemSchema),
})

export type ShoppingItem = z.infer<typeof shoppingItemSchema>
export type ShoppingList = z.infer<typeof shoppingListSchema>

/** The list as it will be once the server agrees: shown at once, replaced by the server's answer. */
export function withChecked(list: ShoppingList, itemId: string, checked: boolean): ShoppingList {
  const items = list.items.map((item) =>
    item.id === itemId ? { ...item, checked, checkedBy: checked ? item.checkedBy : null } : item,
  )
  return { ...list, items, remaining: items.filter((item) => !item.checked).length }
}

/** Items grouped by aisle, in the order the server sent them (already sorted by aisle). */
export function groupByAisle(items: ShoppingItem[]): [string, ShoppingItem[]][] {
  const groups = new Map<string, ShoppingItem[]>()
  for (const item of items) {
    groups.set(item.aisle, [...(groups.get(item.aisle) ?? []), item])
  }
  return [...groups.entries()]
}

// Every Kitchen shopping list tool returns { list, pageUrl }.
const toolResultSchema = z.object({ list: shoppingListSchema })

/** The list in a tool result, or null if the result is not one (checked: it went through the host). */
export function listOf(structuredContent: unknown): ShoppingList | null {
  const parsed = toolResultSchema.safeParse(structuredContent)
  return parsed.success ? parsed.data.list : null
}
