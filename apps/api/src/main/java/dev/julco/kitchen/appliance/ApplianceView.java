package dev.julco.kitchen.appliance;

import java.util.UUID;

/** A household appliance with its confirmed spec sheet. */
public record ApplianceView(
        UUID id, String name, String brandModel, ApplianceSpec spec, UsagePreference usagePreference) {

    static ApplianceView of(Appliance appliance) {
        return new ApplianceView(
                appliance.getId(),
                appliance.getName(),
                appliance.getBrandModel(),
                appliance.getSpec(),
                appliance.getUsagePreference());
    }
}
