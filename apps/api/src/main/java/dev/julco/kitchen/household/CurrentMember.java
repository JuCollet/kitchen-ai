package dev.julco.kitchen.household;

import dev.julco.kitchen.common.Language;
import java.util.Objects;
import java.util.UUID;

/**
 * Who is making the request: resolved once per request from the session (browser) or the OAuth
 * token (AI client), never from a parameter sent by the client (CLAUDE.md rule 1).
 *
 * <p>Every business service method that touches household or personal data takes it as an explicit
 * first parameter, and filters with {@link #householdId()} or {@link #userId()}.
 */
public record CurrentMember(UUID userId, UUID householdId, MemberRole role, Language language) {

    public CurrentMember {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(householdId, "householdId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(language, "language");
    }

    public boolean isOwner() {
        return role == MemberRole.OWNER;
    }
}
