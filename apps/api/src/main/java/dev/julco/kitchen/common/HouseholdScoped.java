package dev.julco.kitchen.common;

import java.util.UUID;

/**
 * Marks an entity that belongs to a household. Its repository must filter every read by
 * household id (CLAUDE.md rule 1), which the architecture tests enforce.
 */
public interface HouseholdScoped {

    UUID getHouseholdId();
}
