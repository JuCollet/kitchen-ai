package dev.julco.kitchen.appliance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Spec sheet of an appliance, written by the AI and confirmed by the user. Jackson annotations
 * describe the MCP tool schema too (see {@link ApplianceMode}).
 */
public record ApplianceSpec(
        @JsonProperty(required = true) @JsonPropertyDescription("The appliance's real modes, 1 to 30.")
        @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid ApplianceMode> modes,
        @JsonProperty @JsonPropertyDescription("Capacity, for example '5.7 L' or '4 servings'.")
        @Size(max = 200) String capacity,
        @JsonProperty @JsonPropertyDescription("Anything else useful: accessories, limits. Max 2000 characters.")
        @Size(max = 2000) String remarks) {

    public ApplianceSpec {
        modes = modes == null ? null : List.copyOf(modes);
    }
}
