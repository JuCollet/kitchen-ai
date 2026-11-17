package dev.julco.kitchen.recipe;

import dev.julco.kitchen.common.Language;
import java.util.List;
import java.util.UUID;

/** A recipe's editable fields once checked and normalised by {@link RecipeService}. */
record RecipeContent(
        UUID applianceId,
        String title,
        String summary,
        short servings,
        Short activeMinutes,
        Short totalMinutes,
        List<Ingredient> ingredients,
        List<RecipeStep> steps,
        List<String> tags,
        NutritionEstimate nutritionEstimate,
        List<String> benefits,
        Language language,
        String source) {}
