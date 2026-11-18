package dev.julco.kitchen.export;

import dev.julco.kitchen.mcp.ToolRunner;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** MCP adapter of {@link ExportService}. */
@Component
class ExportTools {

    private static final String NOT_FOUND =
            "Menu not found in this household: get_context gives the latest menu; create one with create_menu.";

    private final ExportService exports;
    private final ToolRunner runner;
    private final String publicUrl;

    ExportTools(ExportService exports, ToolRunner runner, @Value("${kitchen.public-url}") String publicUrl) {
        this.exports = exports;
        this.runner = runner;
        this.publicUrl = publicUrl;
    }

    @McpTool(
            name = "export_menu_pdf",
            title = "Get the menu as a PDF",
            description = """
                    Creates a printable PDF of a menu of the household, in the connected \
                    person's language: the meal plan, the menu's shopping list by aisle with \
                    checkboxes, and one sheet per recipe with quantities for the menu's portions, \
                    steps, appliance settings, times and estimated nutrition. Returns a download \
                    link valid for 15 minutes: give it to the person as a link. The PDF includes \
                    the latest shopping list created for the menu (includesShoppingList); create \
                    it first with create_shopping_list if the person wants it in the PDF. Without \
                    menuId, uses the latest menu.""")
    CallToolResult exportMenuPdf(
            @McpToolParam(required = false, description = "Menu id; omit for the latest menu.") UUID menuId) {
        return runner.run(NOT_FOUND, member -> {
            MenuPdfLink link = exports.createMenuLink(member, menuId);
            return new PdfLinkResult(publicUrl + "/exports/" + link.token(), link.expiresAt(), link.menuTitle(),
                    link.includesShoppingList());
        });
    }

    record PdfLinkResult(String url, Instant expiresAt, String menuTitle, boolean includesShoppingList) {}
}
