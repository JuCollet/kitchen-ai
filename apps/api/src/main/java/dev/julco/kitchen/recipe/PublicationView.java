package dev.julco.kitchen.recipe;

import java.time.Instant;
import java.util.UUID;

/**
 * State of a recipe's publication: {@code sharedRecipeId} is null once it is withdrawn;
 * {@code signature} null when anonymous.
 */
public record PublicationView(
        UUID recipeId, UUID sharedRecipeId, boolean published, Instant publishedAt, Signature signature) {}
