package dev.julco.kitchen.household;

import java.util.UUID;

/** The current member's household, as shown to them. */
public record HouseholdSummary(UUID id, String name, MemberRole role) {}
