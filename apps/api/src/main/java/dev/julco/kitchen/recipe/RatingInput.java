package dev.julco.kitchen.recipe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A person's rating after cooking a recipe, out of 10: 9 = perfect, nothing to change; 10 =
 * reserved for the best recipe ever tasted. Without a date, cooked today.
 */
public record RatingInput(
        @NotNull UUID recipeId,
        @NotNull @Min(1) @Max(10) Integer score,
        @Size(max = 2000) String comment,
        @Size(max = 2000) String adjustments,
        @PastOrPresent LocalDate cookedOn) {}
