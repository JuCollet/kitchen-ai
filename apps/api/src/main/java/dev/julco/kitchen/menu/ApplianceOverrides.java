package dev.julco.kitchen.menu;

import java.util.List;
import java.util.UUID;

/**
 * Appliances required or excluded for the period of a menu ("only the Cookeo this week"),
 * without changing the permanent preferences.
 */
public record ApplianceOverrides(List<UUID> required, List<UUID> excluded) {

    public static final ApplianceOverrides NONE = new ApplianceOverrides(List.of(), List.of());

    public ApplianceOverrides {
        required = required == null ? List.of() : List.copyOf(required);
        excluded = excluded == null ? List.of() : List.copyOf(excluded);
    }
}
