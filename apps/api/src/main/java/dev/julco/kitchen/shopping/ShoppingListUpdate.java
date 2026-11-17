package dev.julco.kitchen.shopping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Changes to a list in one call: new title, items to add, to remove and to edit. */
public record ShoppingListUpdate(
        @NotNull UUID listId,
        @Size(min = 1, max = 200) String title,
        @Size(max = ShoppingListService.MAX_ITEMS) List<@NotNull @Valid ShoppingItemInput> add,
        @Size(max = ShoppingListService.MAX_ITEMS) List<@NotNull UUID> remove,
        @Size(max = ShoppingListService.MAX_ITEMS) List<@NotNull @Valid ShoppingItemEdit> edit) {

    public ShoppingListUpdate {
        add = add == null ? List.of() : add;
        remove = remove == null ? List.of() : remove;
        edit = edit == null ? List.of() : edit;
    }
}
