package dev.julco.kitchen.shopping;

import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.mcp.McpApps;
import dev.julco.kitchen.mcp.ToolRunner;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.MetaProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MCP adapter of {@link ShoppingListService}, and the shopping list widget (MCP App) that
 * get_shopping_list declares. Every tool returns the same shape, {list, pageUrl}, so the widget can
 * read any of their results.
 */
@Component
class ShoppingListTools {

    static final String WIDGET_URI = "ui://kitchen/shopping-list.html";

    private static final String NOT_FOUND = "Shopping list not found in this household: get_context gives the "
            + "latest one; create one with create_shopping_list.";

    private final ShoppingListService lists;
    private final ToolRunner runner;
    private final String publicUrl;

    ShoppingListTools(ShoppingListService lists, ToolRunner runner, @Value("${kitchen.public-url}") String publicUrl) {
        this.lists = lists;
        this.runner = runner;
        this.publicUrl = publicUrl;
    }

    @McpTool(
            name = "create_shopping_list",
            title = "Create a shopping list",
            description = """
                    Creates a shopping list shared by the connected person's household, linked to \
                    a menu or not. Write it like a real list: quantities added up across recipes \
                    and adjusted to the portions, one line per product, each with its store \
                    aisle; leave out what the pantry already has and the household's staple \
                    ingredients. Then show it with get_shopping_list. Returns the list.""")
    CallToolResult createShoppingList(
            @McpToolParam(description = "Title, for example 'Groceries for the week of 23 November'. Max 200 characters.")
                    String title,
            @McpToolParam(description = "The items, 1 to 200.") List<ShoppingItemInput> items,
            @McpToolParam(required = false, description = "Id of the menu this list is for (get_context, get_menu).")
                    UUID menuId) {
        return runner.run(NOT_FOUND, member -> result(lists.create(member, new ShoppingListInput(menuId, title, items))));
    }

    @McpTool(
            name = "get_shopping_list",
            title = "Show the shopping list",
            description = """
                    Shows a shopping list of the household as an interactive list that every \
                    member can check in the store, synchronised between members and AIs. Always \
                    use this tool to show a shopping list, rather than writing it out. Without \
                    listId, shows the latest list. Also returns pageUrl, a web page with the same \
                    list for apps that cannot display it: give that link to the person if the \
                    list does not appear.""",
            metaProvider = WidgetToolMeta.class)
    CallToolResult getShoppingList(
            @McpToolParam(required = false, description = "List id; omit for the latest list.") UUID listId) {
        return runner.run(NOT_FOUND, member -> result(listId == null
                ? lists.latest(member).orElseThrow(NotFoundException::new)
                : lists.get(member, listId)));
    }

    @McpTool(
            name = "update_shopping_list",
            title = "Change a shopping list",
            description = """
                    Changes a shopping list of the household: new title, items to add, items to \
                    remove and items to edit (by item id, from get_shopping_list; only the given \
                    fields change). Removing a checked item also takes it back out of the \
                    pantry. Returns the list.""")
    CallToolResult updateShoppingList(
            @McpToolParam(description = "List id.") UUID listId,
            @McpToolParam(required = false, description = "New title.") String title,
            @McpToolParam(required = false, description = "Items to add.") List<ShoppingItemInput> add,
            @McpToolParam(required = false, description = "Ids of the items to remove.") List<UUID> remove,
            @McpToolParam(required = false, description = "Items to edit.") List<ShoppingItemEdit> edit) {
        return runner.run(NOT_FOUND, member ->
                result(lists.update(member, new ShoppingListUpdate(listId, title, add, remove, edit))));
    }

    @McpTool(
            name = "check_items",
            title = "Check shopping list items",
            description = """
                    Marks items of a shopping list as bought, for example when the person says \
                    'I got the milk'. A checked item goes into the household's pantry. Items \
                    already checked stay as they are. Returns the list.""")
    CallToolResult checkItems(
            @McpToolParam(description = "List id.") UUID listId,
            @McpToolParam(description = "Ids of the items to check, from get_shopping_list.") List<UUID> itemIds) {
        return runner.run(NOT_FOUND, member -> result(lists.check(member, listId, itemIds)));
    }

    @McpTool(
            name = "uncheck_items",
            title = "Uncheck shopping list items",
            description = """
                    Marks checked items of a shopping list as not bought after all, and takes \
                    back out of the pantry what checking them put in. Returns the list.""")
    CallToolResult uncheckItems(
            @McpToolParam(description = "List id.") UUID listId,
            @McpToolParam(description = "Ids of the items to uncheck, from get_shopping_list.") List<UUID> itemIds) {
        return runner.run(NOT_FOUND, member -> result(lists.uncheck(member, listId, itemIds)));
    }

    @McpResource(
            uri = WIDGET_URI,
            name = "shopping_list_widget",
            title = "Kitchen shopping list",
            description = "Interactive shopping list of the household (MCP App).",
            mimeType = McpApps.MIME_TYPE,
            metaProvider = WidgetResourceMeta.class)
    ReadResourceResult widget() {
        return new ReadResourceResult(List.of(new TextResourceContents(
                WIDGET_URI, McpApps.MIME_TYPE, McpApps.widgetHtml("shopping-list.html"), McpApps.resourceMeta())));
    }

    private ShoppingListResult result(ShoppingListView list) {
        return new ShoppingListResult(list, publicUrl + "/list/" + list.id());
    }

    /** The list, and the web page showing it for clients without MCP Apps. */
    record ShoppingListResult(ShoppingListView list, String pageUrl) {}

    /** Instantiated by reflection by Spring AI: needs a public no-argument constructor. */
    public static final class WidgetToolMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return McpApps.toolMeta(WIDGET_URI);
        }
    }

    public static final class WidgetResourceMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return McpApps.resourceMeta();
        }
    }
}
