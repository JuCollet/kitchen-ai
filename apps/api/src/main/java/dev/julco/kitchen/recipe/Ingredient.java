package dev.julco.kitchen.recipe;

import dev.julco.kitchen.common.Aisle;
import java.math.BigDecimal;

public record Ingredient(String name, BigDecimal quantity, String unit, Aisle aisle, String note) {}
