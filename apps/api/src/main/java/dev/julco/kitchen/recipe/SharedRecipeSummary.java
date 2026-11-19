package dev.julco.kitchen.recipe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recipe published by another household, in short. {@code averageRating}: average of the latest
 * rating of each person who cooked it, in the origin household or in a household that imported it
 * ({@code ratingCount} ratings); never an individual rating. {@code importedAs}: the id of the
 * copy in the connected person's book, if they imported it already. {@code author}: the name the
 * publication is signed with (household or person, as its publisher chose), null when anonymous.
 */
public record SharedRecipeSummary(
        UUID id,
        String title,
        String language,
        String author,
        String summary,
        List<String> tags,
        int servings,
        Integer totalMinutes,
        String applianceModel,
        BigDecimal averageRating,
        int ratingCount,
        UUID importedAs,
        Instant publishedAt) {}
