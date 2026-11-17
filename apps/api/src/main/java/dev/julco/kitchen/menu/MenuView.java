package dev.julco.kitchen.menu;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A menu with its meals in order (day, then breakfast to snack) and the appliances required or
 * excluded for its period.
 */
public record MenuView(
        UUID id,
        String title,
        LocalDate startsOn,
        LocalDate endsOn,
        PeriodAppliances periodAppliances,
        List<MenuMeal> meals,
        Instant createdAt) {

    public record MenuMeal(
            LocalDate servedOn, Meal meal, UUID recipeId, String recipeTitle, UUID applianceId, int servings) {}

    public record PeriodAppliances(List<ApplianceRef> required, List<ApplianceRef> excluded) {}

    public record ApplianceRef(UUID id, String name) {}
}
