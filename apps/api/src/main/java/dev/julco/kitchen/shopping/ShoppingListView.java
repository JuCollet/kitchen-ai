package dev.julco.kitchen.shopping;

import dev.julco.kitchen.common.Aisle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A shopping list with its items in display order: by store aisle, then in the order they were
 * added. {@code remaining} is the number of items not checked yet.
 */
public record ShoppingListView(
        UUID id, String title, UUID menuId, Instant createdAt, int itemCount, int remaining, List<Item> items) {

    /** {@code checkedBy} is the display name of the member who checked the item. */
    public record Item(
            UUID id,
            String name,
            BigDecimal quantity,
            String unit,
            Aisle aisle,
            boolean checked,
            String checkedBy,
            Instant checkedAt) {}
}
