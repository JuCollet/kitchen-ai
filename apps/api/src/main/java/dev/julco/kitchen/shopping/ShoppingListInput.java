package dev.julco.kitchen.shopping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** A new shopping list, linked to a menu of the household or not. Bounded (CLAUDE.md rule 4). */
public record ShoppingListInput(
        UUID menuId,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(min = 1, max = ShoppingListService.MAX_ITEMS) List<@NotNull @Valid ShoppingItemInput> items) {}
