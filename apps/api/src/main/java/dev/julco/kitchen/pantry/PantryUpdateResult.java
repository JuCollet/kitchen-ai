package dev.julco.kitchen.pantry;

import java.util.List;

/** The whole pantry after the update, and the names to remove that it did not contain. */
public record PantryUpdateResult(List<PantryItemView> items, List<String> notFound) {}
