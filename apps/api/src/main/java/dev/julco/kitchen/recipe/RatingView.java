package dev.julco.kitchen.recipe;

import java.time.Instant;

/** One rating in a recipe's history, with the name of the household member who gave it. */
public record RatingView(
        String memberName,
        boolean mine,
        int score,
        String comment,
        String adjustments,
        Instant cookedAt,
        int recipeVersion) {}
