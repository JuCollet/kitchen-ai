package dev.julco.kitchen.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Indicative estimate per serving, provided by the AI and always presented as an estimate. */
public record NutritionEstimate(
        @JsonProperty("calories_kcal") @Min(0) @Max(5000) Integer caloriesKcal,
        @JsonProperty("protein_g") @Min(0) @Max(500) Integer proteinG,
        @JsonProperty("carbs_g") @Min(0) @Max(1000) Integer carbsG,
        @JsonProperty("fat_g") @Min(0) @Max(500) Integer fatG,
        @JsonProperty("fiber_g") @Min(0) @Max(200) Integer fiberG) {}
