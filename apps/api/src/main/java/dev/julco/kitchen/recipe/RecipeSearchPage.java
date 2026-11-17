package dev.julco.kitchen.recipe;

import java.util.List;

/** One page of search results. Ask for {@code page + 1} while {@code hasMore} is true. */
public record RecipeSearchPage(List<RecipeSummary> results, int page, boolean hasMore) {}
