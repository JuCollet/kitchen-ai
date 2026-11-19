package dev.julco.kitchen.recipe;

import java.time.Instant;
import java.util.UUID;

/** One version of a recipe in full, as it was saved. */
public record RecipeVersionDetail(
        UUID recipeId,
        int version,
        boolean current,
        String changeNote,
        Instant savedAt,
        RecipeSnapshot recipe) {}
