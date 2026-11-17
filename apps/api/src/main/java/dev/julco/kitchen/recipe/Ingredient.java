package dev.julco.kitchen.recipe;

import dev.julco.kitchen.common.Aisle;
import java.math.BigDecimal;

/** One ingredient of a stored recipe, for its number of servings. Written from {@link IngredientInput}. */
public record Ingredient(String name, BigDecimal quantity, String unit, Aisle aisle, String note) {}
