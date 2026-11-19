package dev.julco.kitchen.recipe;

import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.mcp.ToolRunner;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP adapter of {@link RecipeService}: argument mapping only. */
@Component
class RecipeTools {

    private static final String NOT_FOUND =
            "Recipe not found in this household's book: use search_recipes to get the ids.";

    private final RecipeService recipes;
    private final ToolRunner runner;

    RecipeTools(RecipeService recipes, ToolRunner runner) {
        this.recipes = recipes;
        this.runner = runner;
    }

    @McpTool(
            name = "search_recipes",
            title = "Search the household's recipe book",
            description = """
                    Searches the recipes saved in the connected person's household book. All \
                    criteria are optional and combined: text (words of the title, ingredients, \
                    tags or summary; accents and plurals do not matter), tags (all required), \
                    appliance, classic recipes only, minimum rating by this person or average of \
                    the household members' latest ratings (out of 10), not cooked for a number \
                    of days. Without criteria, lists the whole book. Returns short summaries \
                    (id, title, scores, last cooked date), 10 per page by default; ask for the \
                    next page while hasMore is true. Use get_recipe for the full recipe. Search \
                    here before inventing a new recipe.""")
    CallToolResult searchRecipes(
            @McpToolParam(required = false, description = "Words to search, for example 'chicken curry'. Max 200 characters.")
                    String text,
            @McpToolParam(required = false, description = "Tags that must all be present, for example ['quick', 'vegetarian'].")
                    List<String> tags,
            @McpToolParam(required = false, description = "Only recipes for this appliance (id from get_context).")
                    UUID applianceId,
            @McpToolParam(required = false, description = "true: only classic recipes, without appliance.")
                    Boolean classicOnly,
            @McpToolParam(required = false, description = "Minimum latest rating by the connected person, 1 to 10.")
                    Integer minMyScore,
            @McpToolParam(required = false, description = "Minimum household average rating, 1 to 10.")
                    Integer minHouseholdScore,
            @McpToolParam(required = false, description = "Only recipes not cooked (rated) for at least this many days, or never.")
                    Integer notCookedForDays,
            @McpToolParam(required = false, description = "Page number, from 0.") Integer page,
            @McpToolParam(required = false, description = "Results per page, 1 to 20 (default 10).") Integer pageSize) {
        return runner.run(NOT_FOUND, member -> recipes.search(member, new RecipeSearch(
                text, tags, applianceId, classicOnly, minMyScore, minHouseholdScore, notCookedForDays, page, pageSize)));
    }

    @McpTool(
            name = "get_recipe",
            title = "Get a recipe",
            description = """
                    Returns a full recipe of the household's book: ingredients for its number of \
                    servings, steps (with appliance modes and temperatures), times, tags, \
                    estimated nutrition, benefit labels, source, version, the confirmed spec \
                    sheet of its appliance, and its rating history by the household members \
                    (latest first, with the recipe version each rating refers to).""")
    CallToolResult getRecipe(@McpToolParam(description = "Recipe id, from search_recipes.") UUID recipeId) {
        return runner.run(NOT_FOUND, member -> recipes.get(member, recipeId));
    }

    @McpTool(
            name = "save_recipe",
            title = "Save a recipe",
            description = """
                    Saves a recipe in the household's book, or edits one (pass its id: the \
                    complete recipe replaces the previous one and becomes a new version). Use it \
                    to import a recipe the person shows you (URL, pasted text, document): read \
                    the source with your own tools, structure it faithfully, and put where it \
                    comes from in source. Only adapt it (appliance, servings) if the person asks. \
                    A recipe linked to an appliance (applianceId) may only use modes of that \
                    appliance's confirmed spec sheet, at temperatures within each mode's range: \
                    the server refuses anything else and lists the real modes. A classic recipe \
                    (pan, pot, oven) has no applianceId and no appliance_mode. Returns the saved \
                    recipe.""")
    CallToolResult saveRecipe(
            @McpToolParam(required = false, description = "Id of the recipe to edit; omit to add a new one.") UUID id,
            @McpToolParam(required = false, description = "Appliance used (id from get_context); omit for a classic recipe.")
                    UUID applianceId,
            @McpToolParam(description = "Title. Max 200 characters.") String title,
            @McpToolParam(required = false, description = "One or two sentences. Max 1000 characters.") String summary,
            @McpToolParam(description = "Number of servings the quantities are for, 1 to 50.") Integer servings,
            @McpToolParam(required = false, description = "Hands-on time in minutes.") Integer activeMinutes,
            @McpToolParam(required = false, description = "Total time in minutes, waiting included.") Integer totalMinutes,
            @McpToolParam(description = "Ingredients, 1 to 60.") List<IngredientInput> ingredients,
            @McpToolParam(description = "Steps in order, 1 to 40.") List<RecipeStep> steps,
            @McpToolParam(required = false, description = "Lowercase tags, max 20. Always include exactly one course tag: "
                    + "main, starter, side, soup, salad, dessert, baking, breakfast, snack, drink or sauce; then any "
                    + "other, for example 'quick', 'vegetarian', 'batch cooking'.")
                    List<String> tags,
            @McpToolParam(required = false, description = "Estimated nutrition per serving.") NutritionEstimate nutritionEstimate,
            @McpToolParam(required = false, description = "Benefit labels, for example 'high protein', 'rich in fibre'. Max 10.")
                    List<String> benefits,
            @McpToolParam(required = false, description = "Language the recipe is written in: fr, nl or en. Defaults to the person's.")
                    String language,
            @McpToolParam(required = false, description = "Where the recipe comes from: URL, book, person. Max 500 characters.")
                    String source,
            @McpToolParam(required = false, description = "When editing: what changed, in a few words, for the recipe's "
                    + "history, for example 'olive oil replaced by rapeseed oil'. Max 300 characters.")
                    String changeNote) {
        return runner.run(NOT_FOUND, member -> recipes.save(member, new RecipeInput(
                id, applianceId, title, summary, servings, activeMinutes, totalMinutes, ingredients, steps, tags,
                nutritionEstimate, benefits, language(language), source), changeNote));
    }

    @McpTool(
            name = "list_recipe_versions",
            title = "Show a recipe's history",
            description = """
                    Lists the saved versions of a recipe of the household, latest first (up to \
                    the last 50): version number, what changed (changeNote), who saved it and \
                    when, and how that version was rated (average of each member's latest \
                    rating of it, with ratingCount). Use it to compare versions and to find the \
                    best one, then get_recipe_version to read one in full.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Show a recipe's history", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult listRecipeVersions(
            @McpToolParam(description = "Recipe id, from search_recipes.") UUID recipeId) {
        return runner.run(NOT_FOUND, member -> recipes.versions(member, recipeId));
    }

    @McpTool(
            name = "get_recipe_version",
            title = "Read an earlier version of a recipe",
            description = """
                    Returns one saved version of a recipe of the household in full (ingredients, \
                    steps, times…), as it was, with its change note. Use it to show the person \
                    what an earlier version looked like before going back to it.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Read an earlier version of a recipe", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult getRecipeVersion(
            @McpToolParam(description = "Recipe id, from search_recipes.") UUID recipeId,
            @McpToolParam(description = "Version number, from list_recipe_versions.") int version) {
        return runner.run(NOT_FOUND, member -> recipes.version(member, recipeId, version));
    }

    @McpTool(
            name = "restore_recipe_version",
            title = "Go back to an earlier version of a recipe",
            description = """
                    Makes an earlier version of a recipe the current one again, when the person \
                    asks to undo a change ('the rapeseed oil was not good, go back'). Nothing is \
                    lost: the old content is saved as a new version, the versions in between \
                    stay in the history, and ratings stay attached to the versions they judged. \
                    The appliance rules apply as for save_recipe. Returns the recipe.""")
    CallToolResult restoreRecipeVersion(
            @McpToolParam(description = "Recipe id, from search_recipes.") UUID recipeId,
            @McpToolParam(description = "Version number to go back to, from list_recipe_versions.") int version) {
        return runner.run(NOT_FOUND, member -> recipes.restore(member, recipeId, version));
    }

    @McpTool(
            name = "delete_recipe",
            title = "Delete a recipe",
            description = """
                    Deletes a recipe from the household's book, for example one saved by \
                    mistake or a duplicate. Always in two calls. First call it without \
                    confirmedByUser: nothing is deleted, and the result says what would go with \
                    the recipe (its ratings, its meals in the household's menus, its publication \
                    for other households). Tell the person, ask them to confirm explicitly, and \
                    only after their yes call it again with confirmedByUser true. Never set \
                    confirmedByUser true without that yes in this conversation. Copies other \
                    households imported stay in their books.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Delete a recipe", destructiveHint = true, readOnlyHint = false, idempotentHint = false,
                    openWorldHint = false))
    CallToolResult deleteRecipe(
            @McpToolParam(description = "Recipe id, from search_recipes.") UUID recipeId,
            @McpToolParam(required = false, description = "true only after the person explicitly confirmed the deletion.")
                    Boolean confirmedByUser) {
        return runner.run(NOT_FOUND, member ->
                recipes.delete(member, recipeId, Boolean.TRUE.equals(confirmedByUser)));
    }

    @McpTool(
            name = "rate_recipe",
            title = "Rate a recipe",
            description = """
                    Records the connected person's rating of a recipe they cooked, out of 10: 9 \
                    means perfect, nothing to change; 10 is reserved for the best recipe they \
                    ever tasted. Ask for the score, what they would change (adjustments) and any \
                    comment. Each cooking adds a rating to the history; the latest one counts as \
                    the person's score. Returns the rating.""")
    CallToolResult rateRecipe(
            @McpToolParam(description = "Recipe id, from search_recipes or get_recipe.") UUID recipeId,
            @McpToolParam(description = "Score from 1 to 10.") Integer score,
            @McpToolParam(required = false, description = "What the person thought. Max 2000 characters.") String comment,
            @McpToolParam(required = false, description = "What to change next time. Max 2000 characters.") String adjustments,
            @McpToolParam(required = false, description = "Date it was cooked, YYYY-MM-DD. Defaults to today.")
                    LocalDate cookedOn) {
        return runner.run(NOT_FOUND, member -> recipes.rate(member,
                new RatingInput(recipeId, score, comment, adjustments, cookedOn)));
    }

    private static Language language(String code) {
        return code == null ? null : Language.fromCode(code);
    }
}
