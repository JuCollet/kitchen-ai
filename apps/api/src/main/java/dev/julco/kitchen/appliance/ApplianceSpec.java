package dev.julco.kitchen.appliance;

import java.util.List;

/** Spec sheet of an appliance, written by the AI and confirmed by the user. */
public record ApplianceSpec(List<ApplianceMode> modes, String capacity, String remarks) {

    public ApplianceSpec {
        modes = modes == null ? List.of() : List.copyOf(modes);
    }
}
