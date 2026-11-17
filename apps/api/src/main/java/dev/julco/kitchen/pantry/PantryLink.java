package dev.julco.kitchen.pantry;

import java.util.UUID;

/**
 * What a checked shopping list item did to the pantry, so that unchecking it can undo exactly
 * that: {@code created} = the item did not exist (delete it); otherwise restore
 * {@code previousQuantity}.
 */
public record PantryLink(UUID pantryItemId, boolean created, String previousQuantity) {}
