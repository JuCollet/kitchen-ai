package dev.julco.kitchen.profile;

import java.util.List;
import java.util.UUID;

/**
 * What a household member cannot or does not want to eat. Shared with the other members' AIs:
 * household menus respect the union of these constraints (docs/SCOPE.md). Allergies are only
 * present when the member consented, and the consent text says they are shared this way.
 */
public record MemberConstraints(
        UUID userId, String displayName, String diet, List<String> allergies, List<String> dislikes) {}
