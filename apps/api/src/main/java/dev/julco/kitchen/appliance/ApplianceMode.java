package dev.julco.kitchen.appliance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A real mode of an appliance, as confirmed by the user. Temperatures in °C. The name is a stable
 * code ("air_fry") that recipe steps reference (docs/SCOPE.md: no invented mode).
 *
 * <p>The Jackson annotations also describe the MCP tool schema: {@code required} and the
 * descriptions are what the AI sees.
 */
public record ApplianceMode(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Lowercase code of a real mode of the appliance, for example air_fry, steam, pressure_cook.")
        @NotNull @Pattern(regexp = "[a-z0-9_]{1,40}", message = "must be a lowercase code like air_fry")
        String name,
        @JsonProperty("temp_min") @JsonPropertyDescription("Lowest temperature of this mode in °C, if it has one.")
        @Min(0) @Max(350) Integer tempMin,
        @JsonProperty("temp_max") @JsonPropertyDescription("Highest temperature of this mode in °C, if it has one.")
        @Min(0) @Max(350) Integer tempMax,
        @JsonProperty @JsonPropertyDescription("What the mode is for, its limits. Max 500 characters.")
        @Size(max = 500) String notes) {}
