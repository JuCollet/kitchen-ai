package dev.julco.kitchen.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Indicative estimate per serving, provided by the AI and always presented as an estimate. */
public record NutritionEstimate(
        @JsonProperty("calories_kcal") Integer caloriesKcal,
        @JsonProperty("protein_g") Integer proteinG,
        @JsonProperty("carbs_g") Integer carbsG,
        @JsonProperty("fat_g") Integer fatG,
        @JsonProperty("fiber_g") Integer fiberG) {}
