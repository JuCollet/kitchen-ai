package dev.julco.kitchen.menu;

import java.time.LocalDate;
import java.util.UUID;

/** A recipe served recently in a menu that the connected person has not rated since. */
public record RecipeToRate(UUID recipeId, String title, LocalDate servedOn, Meal meal) {}
