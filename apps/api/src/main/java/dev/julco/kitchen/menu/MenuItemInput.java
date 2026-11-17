package dev.julco.kitchen.menu;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A meal of a menu, as written by the AI. The meal is a plain code, not the {@link Meal} enum:
 * the MCP schema generator would list the Java constant names (DINNER) instead of the codes
 * clients must send (dinner).
 */
public record MenuItemInput(
        @JsonProperty(required = true) @JsonPropertyDescription("Day of the meal, YYYY-MM-DD, within the menu's period.")
        @NotNull LocalDate servedOn,
        @JsonProperty(required = true) @JsonPropertyDescription("breakfast, lunch, dinner or snack.")
        @NotNull @Pattern(regexp = "breakfast|lunch|dinner|snack", message = "must be one of breakfast, lunch, dinner, snack")
        String meal,
        @JsonProperty(required = true) @JsonPropertyDescription("Id of a recipe of the household's book (search_recipes, save_recipe).")
        @NotNull UUID recipeId,
        @JsonProperty(required = true) @JsonPropertyDescription("Number of portions to cook, 1 to 50.")
        @NotNull @Min(1) @Max(50) Integer servings) {}
