package dev.julco.kitchen.recipe;

import java.util.UUID;

/** What another feature (menus) needs to know about a recipe of the household's book. */
public record RecipeBrief(UUID id, String title, UUID applianceId, int servings) {}
