package dev.julco.kitchen.household;

import java.time.Instant;
import java.util.UUID;

/** A household member as seen by the other members. */
public record MemberSummary(UUID userId, String displayName, MemberRole role, Instant joinedAt) {}
