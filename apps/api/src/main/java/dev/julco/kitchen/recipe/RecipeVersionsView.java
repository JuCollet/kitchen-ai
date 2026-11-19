package dev.julco.kitchen.recipe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recipe's history, latest version first. Each version has its change note, who saved it, and
 * how it was rated: the average of the latest rating of each member who cooked that version.
 */
public record RecipeVersionsView(UUID recipeId, String title, int currentVersion, List<Version> versions) {

    public record Version(
            int version,
            boolean current,
            String title,
            String changeNote,
            String savedBy,
            Instant savedAt,
            BigDecimal householdScore,
            int ratingCount) {}
}
