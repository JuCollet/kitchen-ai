package dev.julco.kitchen.recipe;

import java.util.List;

/**
 * A page of shared recipes. {@code notice} reminds the AI that this content was written by other
 * households (CLAUDE.md rule 6).
 */
public record SharedRecipePage(List<SharedRecipeSummary> results, int page, boolean hasMore, String notice) {}
