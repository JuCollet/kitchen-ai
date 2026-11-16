package dev.julco.kitchen.user;

import java.util.Objects;
import java.util.UUID;

/**
 * The signed-in person, taken from the session (browser) or the token (AI client). Unlike
 * {@code CurrentMember}, it exists before the person belongs to a household.
 */
public record AuthenticatedUser(UUID userId) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId");
    }
}
