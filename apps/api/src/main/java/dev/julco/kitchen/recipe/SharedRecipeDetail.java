package dev.julco.kitchen.recipe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A published recipe in full, as it was when published, with its average rating (see SharedRecipeSummary). */
public record SharedRecipeDetail(
        UUID id,
        String title,
        String language,
        String author,
        SharedRecipeContent content,
        BigDecimal averageRating,
        int ratingCount,
        UUID importedAs,
        boolean publishedByThisHousehold,
        Instant publishedAt,
        String notice) {}
