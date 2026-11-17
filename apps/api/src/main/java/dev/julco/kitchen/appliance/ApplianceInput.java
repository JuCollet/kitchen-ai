package dev.julco.kitchen.appliance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * An appliance to create ({@code id} null) or to replace. The spec sheet is only stored once the
 * person has read and confirmed it (docs/SCOPE.md, "Appliances").
 */
public record ApplianceInput(
        UUID id,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String brandModel,
        @NotNull @Valid ApplianceSpec spec,
        UsagePreference usagePreference,
        @AssertTrue(message = "the person must read and confirm the spec sheet first") boolean confirmedByUser) {}
