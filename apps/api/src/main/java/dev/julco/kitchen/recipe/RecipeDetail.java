package dev.julco.kitchen.recipe;

import dev.julco.kitchen.appliance.ApplianceView;
import dev.julco.kitchen.common.Language;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A full recipe, with the spec sheet of its appliance and its rating history (latest first). */
public record RecipeDetail(
        UUID id,
        String title,
        String summary,
        int servings,
        Integer activeMinutes,
        Integer totalMinutes,
        List<Ingredient> ingredients,
        List<RecipeStep> steps,
        List<String> tags,
        NutritionEstimate nutritionEstimate,
        List<String> benefits,
        Language language,
        String source,
        int version,
        Instant updatedAt,
        ApplianceView appliance,
        Integer myScore,
        BigDecimal householdScore,
        List<RatingView> ratings) {}
