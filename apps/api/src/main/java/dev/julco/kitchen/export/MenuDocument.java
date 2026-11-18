package dev.julco.kitchen.export;

import java.util.List;

/**
 * Everything the PDF template shows, already formatted for the reader's language (dates, numbers,
 * meal and aisle names): the template only places text, always escaped (th:text).
 */
record MenuDocument(
        String title, String period, String lang, List<Day> days, Shopping shopping, List<RecipeSheet> recipes) {

    record Day(String date, List<MealLine> meals) {}

    record MealLine(String meal, String recipe, int servings) {}

    /** The menu's shopping list, or null when it has none. */
    record Shopping(String title, List<AisleGroup> aisles) {}

    record AisleGroup(String name, List<ShoppingLine> items) {}

    record ShoppingLine(String name, String quantity, boolean checked) {}

    record RecipeSheet(
            String title,
            String summary,
            int servings,
            Integer activeMinutes,
            Integer totalMinutes,
            String appliance,
            List<IngredientLine> ingredients,
            List<StepLine> steps,
            Nutrition nutrition,
            List<String> benefits,
            String source) {}

    record IngredientLine(String quantity, String name, String note) {}

    /** {@code settings}: appliance mode, temperature and duration, for example "air_fry · 200 °C · 20 min". */
    record StepLine(String text, String settings) {}

    record Nutrition(Integer caloriesKcal, Integer proteinG, Integer carbsG, Integer fatG, Integer fiberG) {}
}
