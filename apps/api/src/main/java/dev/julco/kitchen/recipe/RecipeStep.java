package dev.julco.kitchen.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One step of a recipe. {@code applianceMode} must be a mode of the linked appliance's confirmed
 * spec sheet, and the temperature within that mode's range (CLAUDE.md rule 5). Temperature in °C.
 */
public record RecipeStep(
        @JsonProperty(required = true) @JsonPropertyDescription("What to do, in one or a few sentences.")
        @NotBlank @Size(max = 2000) String text,
        @JsonProperty("duration_min") @JsonPropertyDescription("Duration of the step in minutes.")
        @Min(0) @Max(1440) Integer durationMin,
        @JsonProperty("appliance_mode")
        @JsonPropertyDescription("Code of the mode of the recipe's appliance used in this step, exactly as in "
                + "its spec sheet (for example air_fry). Only when the recipe is linked to an appliance.")
        @Size(max = 40) String applianceMode,
        @JsonProperty @JsonPropertyDescription("Temperature in °C, within the mode's range if a mode is used.")
        @Min(0) @Max(350) Integer temperature) {}
