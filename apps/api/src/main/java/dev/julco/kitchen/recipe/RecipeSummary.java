package dev.julco.kitchen.recipe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A search result: enough to choose, not the whole recipe. {@code myScore} is the person's latest
 * rating; {@code householdScore} the average of each member's latest rating.
 */
public record RecipeSummary(
        UUID id,
        String title,
        String summary,
        UUID applianceId,
        List<String> tags,
        int servings,
        Integer totalMinutes,
        int version,
        Integer myScore,
        BigDecimal householdScore,
        int raterCount,
        Instant lastCookedAt) {}
