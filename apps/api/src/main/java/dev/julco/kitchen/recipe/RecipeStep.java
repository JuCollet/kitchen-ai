package dev.julco.kitchen.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One step of a recipe. {@code applianceMode} must be a mode of the linked appliance's confirmed
 * spec sheet (CLAUDE.md rule 5). Temperature in °C.
 */
public record RecipeStep(
        String text,
        @JsonProperty("duration_min") Integer durationMin,
        @JsonProperty("appliance_mode") String applianceMode,
        Integer temperature) {}
