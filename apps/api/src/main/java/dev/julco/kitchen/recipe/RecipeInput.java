package dev.julco.kitchen.recipe;

import dev.julco.kitchen.common.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * A recipe to create ({@code id} null) or to edit, as written by the AI. Every list and text is
 * bounded (CLAUDE.md rule 4). Without a language, the recipe takes the person's.
 */
public record RecipeInput(
        UUID id,
        UUID applianceId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String summary,
        @NotNull @Min(1) @Max(50) Integer servings,
        @Min(0) @Max(1440) Integer activeMinutes,
        @Min(0) @Max(10080) Integer totalMinutes,
        @NotNull @Size(min = 1, max = 60) List<@NotNull @Valid IngredientInput> ingredients,
        @NotNull @Size(min = 1, max = 40) List<@NotNull @Valid RecipeStep> steps,
        @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
        @Valid NutritionEstimate nutritionEstimate,
        @Size(max = 10) List<@NotBlank @Size(max = 60) String> benefits,
        Language language,
        @Size(max = 500) String source) {}
