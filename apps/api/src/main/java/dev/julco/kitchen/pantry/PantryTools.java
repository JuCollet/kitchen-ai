package dev.julco.kitchen.pantry;

import dev.julco.kitchen.mcp.ToolRunner;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP adapter of {@link PantryService}: argument mapping only. */
@Component
class PantryTools {

    private static final String NOT_FOUND = "Pantry not found.";

    private final PantryService pantry;
    private final ToolRunner runner;

    PantryTools(PantryService pantry, ToolRunner runner) {
        this.pantry = pantry;
        this.runner = runner;
    }

    @McpTool(
            name = "get_pantry",
            title = "Get the household's pantry",
            description = """
                    Returns what the connected person's household has at home: every pantry item \
                    with its approximate quantity, store aisle and last update. Use it to plan \
                    meals with what is left and to leave out of a shopping list what the \
                    household already has. Returns {items: [...]}. Takes no arguments.""")
    CallToolResult getPantry() {
        return runner.run(NOT_FOUND, member -> new Pantry(pantry.list(member)));
    }

    @McpTool(
            name = "update_pantry",
            title = "Update the household's pantry",
            description = """
                    Changes the household's pantry in one call. upsert adds items, or updates \
                    the quantity and aisle of an item the pantry already has with the same name \
                    (case does not matter). remove takes out items by name, for example what was \
                    used in a recipe that was just cooked; names the pantry did not contain are \
                    returned in notFound. Quantities are approximate words ('half a bag'). Use it \
                    when the person says what they have, bought or used up; ask before removing \
                    what you only assume was used. Returns the whole pantry.""")
    CallToolResult updatePantry(
            @McpToolParam(required = false, description = "Items to add or update, at most 100.")
                    List<PantryUpsert> upsert,
            @McpToolParam(required = false, description = "Names of the items to remove, at most 100.")
                    List<String> remove) {
        return runner.run(NOT_FOUND, member -> pantry.update(member, new PantryUpdate(upsert, remove)));
    }

    /** MCP structured content must be a JSON object, never a bare array. */
    record Pantry(List<PantryItemView> items) {}
}
