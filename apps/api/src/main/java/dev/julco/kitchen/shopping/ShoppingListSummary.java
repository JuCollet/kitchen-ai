package dev.julco.kitchen.shopping;

import java.util.UUID;

/** The latest shopping list in short, for get_context. */
public record ShoppingListSummary(UUID id, String title, int itemCount, int remaining) {}
