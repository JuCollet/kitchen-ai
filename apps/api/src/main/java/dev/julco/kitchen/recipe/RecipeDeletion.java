package dev.julco.kitchen.recipe;

import java.util.UUID;

/**
 * What deleting a recipe does, or did. {@code deleted} false: nothing was deleted yet, the AI must
 * show this to the person and ask them to confirm. The counts are what goes (or went) with it:
 * its ratings, its meals in the household's menus, and its publication for other households
 * (copies other households imported stay in their books).
 */
public record RecipeDeletion(
        UUID recipeId,
        String title,
        boolean deleted,
        int ratings,
        int menuMeals,
        boolean published,
        String message) {}
