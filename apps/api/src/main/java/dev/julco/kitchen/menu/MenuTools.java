package dev.julco.kitchen.menu;

import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.mcp.ToolRunner;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP adapter of {@link MenuService}: argument mapping only. */
@Component
class MenuTools {

    private static final String NOT_FOUND =
            "Menu not found in this household. get_context gives the latest menu; create one with create_menu.";

    private final MenuService menus;
    private final ToolRunner runner;

    MenuTools(MenuService menus, ToolRunner runner) {
        this.menus = menus;
        this.runner = runner;
    }

    @McpTool(
            name = "create_menu",
            title = "Save a menu",
            description = """
                    Saves a menu for the connected person's household: a period (at most 31 \
                    days) and its meals, each one a recipe already saved in the household's book \
                    (save new recipes with save_recipe first, then use their ids here). To change \
                    a menu, call again with its menuId and the complete new menu: it replaces \
                    the old one. requiredApplianceIds and excludedApplianceIds hold the appliance \
                    choices for this period only ("only the Cookeo this week", "no Speedi"), \
                    without changing the permanent preferences; a recipe using an excluded \
                    appliance is refused. Show the plan to the person and get their agreement \
                    before saving. Returns the saved menu.""")
    CallToolResult createMenu(
            @McpToolParam(required = false, description = "Id of the menu to replace; omit to create a new one.")
                    UUID menuId,
            @McpToolParam(description = "Title, for example 'Week of 23 November'. Max 200 characters.") String title,
            @McpToolParam(description = "First day of the period, YYYY-MM-DD.") LocalDate startsOn,
            @McpToolParam(description = "Last day of the period, YYYY-MM-DD, at most 31 days after startsOn.")
                    LocalDate endsOn,
            @McpToolParam(description = "The meals, 1 to 100: day, meal, recipe id and number of portions.")
                    List<MenuItemInput> items,
            @McpToolParam(required = false, description = "Appliances to favour for this period (ids from get_context).")
                    List<UUID> requiredApplianceIds,
            @McpToolParam(required = false, description = "Appliances not to use during this period (ids from get_context).")
                    List<UUID> excludedApplianceIds) {
        return runner.run(NOT_FOUND, member -> menus.save(member, new MenuInput(
                menuId, title, startsOn, endsOn, requiredApplianceIds, excludedApplianceIds, items)));
    }

    @McpTool(
            name = "get_menu",
            title = "Get a menu",
            description = """
                    Returns a menu of the connected person's household with its meals in order \
                    (day, meal, recipe id and title, appliance, portions) and the appliances \
                    required or excluded for its period. Without menuId, returns the latest \
                    menu. Use get_recipe for the full recipes.""")
    CallToolResult getMenu(
            @McpToolParam(required = false, description = "Menu id; omit for the latest menu.") UUID menuId) {
        return runner.run(NOT_FOUND, member -> menuId == null
                ? menus.latest(member).orElseThrow(NotFoundException::new)
                : menus.get(member, menuId));
    }
}
