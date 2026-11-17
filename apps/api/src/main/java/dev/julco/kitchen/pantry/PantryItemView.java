package dev.julco.kitchen.pantry;

import dev.julco.kitchen.common.Aisle;
import java.time.Instant;
import java.util.UUID;

/** A pantry item: what the household has at home, with an approximate quantity. */
public record PantryItemView(UUID id, String name, String quantity, Aisle aisle, PantrySource source, Instant updatedAt) {

    static PantryItemView of(PantryItem item) {
        return new PantryItemView(
                item.getId(), item.getName(), item.getQuantity(), item.getAisle(), item.getSource(), item.getUpdatedAt());
    }
}
