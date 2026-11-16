package dev.julco.kitchen.household;

import java.time.Instant;

/** A new invitation. The token is returned only once: only its hash is stored. */
public record CreatedInvitation(String token, Instant expiresAt) {}
