package dev.julco.kitchen.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.julco.kitchen.common.Language;
import java.util.List;
import java.util.UUID;

/**
 * A recipe's editable fields as they were in one version (recipe_version.content, V11), stored in
 * snake_case like the other JSON documents. Restoring a version saves these fields again.
 */
public record RecipeSnapshot(
        @JsonProperty("appliance_id") UUID applianceId,
        String title,
        String summary,
        short servings,
        @JsonProperty("active_minutes") Short activeMinutes,
        @JsonProperty("total_minutes") Short totalMinutes,
        List<Ingredient> ingredients,
        List<RecipeStep> steps,
        List<String> tags,
        @JsonProperty("nutrition_estimate") NutritionEstimate nutritionEstimate,
        List<String> benefits,
        Language language,
        String source) {

    static RecipeSnapshot of(RecipeContent content) {
        return new RecipeSnapshot(
                content.applianceId(),
                content.title(),
                content.summary(),
                content.servings(),
                content.activeMinutes(),
                content.totalMinutes(),
                content.ingredients(),
                content.steps(),
                content.tags(),
                content.nutritionEstimate(),
                content.benefits(),
                content.language(),
                content.source());
    }
}
