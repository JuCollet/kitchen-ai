package dev.julco.kitchen.recipe;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Where a recipe is used, for its deletion: ratings and meals of the household's menus. Plain SQL
 * on purpose: the menu package depends on the recipe package, so the recipe package may not use
 * menu classes (no cycles between features, ArchitectureTest). Every statement is filtered by the
 * household id, which comes from the current member. Only {@link RecipeService} calls this class.
 */
@Component
class RecipeUsageQuery {

    private final JdbcClient jdbc;

    RecipeUsageQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    int ratings(UUID householdId, UUID recipeId) {
        return jdbc.sql("select count(*) from recipe_rating where household_id = :householdId and recipe_id = :recipeId")
                .param("householdId", householdId)
                .param("recipeId", recipeId)
                .query(Integer.class)
                .single();
    }

    int menuMeals(UUID householdId, UUID recipeId) {
        return jdbc.sql("select count(*) from menu_item where household_id = :householdId and recipe_id = :recipeId")
                .param("householdId", householdId)
                .param("recipeId", recipeId)
                .query(Integer.class)
                .single();
    }

    /** Takes the recipe out of the household's menus: the other meals of those menus stay. */
    int deleteMenuMeals(UUID householdId, UUID recipeId) {
        return jdbc.sql("delete from menu_item where household_id = :householdId and recipe_id = :recipeId")
                .param("householdId", householdId)
                .param("recipeId", recipeId)
                .update();
    }
}
