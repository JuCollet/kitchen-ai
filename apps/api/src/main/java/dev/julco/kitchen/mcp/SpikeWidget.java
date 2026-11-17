package dev.julco.kitchen.mcp;

import dev.julco.kitchen.auth.CurrentUsers;
import dev.julco.kitchen.household.HouseholdService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.MetaProvider;
import org.springframework.stereotype.Component;

/**
 * Phase 5 spike: the smallest MCP App. The tool returns the household name and the server time;
 * the host renders the widget with that result, and the widget's "Refresh" button calls the
 * {@code ping} tool back through the host. Removed when the real shopping list lands (phase 9).
 */
@Component
class SpikeWidget {

    static final String RESOURCE_URI = "ui://kitchen/shopping-list.html";

    private final CurrentUsers currentUsers;
    private final HouseholdService households;

    SpikeWidget(CurrentUsers currentUsers, HouseholdService households) {
        this.currentUsers = currentUsers;
        this.households = households;
    }

    @McpTool(
            name = "spike_widget",
            title = "Kitchen widget test",
            description = "Shows a small interactive Kitchen widget, to test that this AI client can "
                    + "display Kitchen widgets. Use it only when the person asks to test the Kitchen "
                    + "widget. Takes no arguments.",
            metaProvider = ToolMeta.class)
    CallToolResult spikeWidget() {
        String household = currentUsers.currentMember()
                .map(member -> households.getHousehold(member).name())
                .orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("household", household);
        data.put("servedAt", Instant.now().toString());
        String text = household == null
                ? "The Kitchen widget is shown. This person does not belong to a household yet."
                : "The Kitchen widget is shown for the household " + household + ".";
        return CallToolResult.builder().addTextContent(text).structuredContent(data).build();
    }

    @McpResource(
            uri = RESOURCE_URI,
            name = "shopping_list_widget",
            title = "Kitchen widget",
            description = "Interactive Kitchen widget (MCP App).",
            mimeType = McpApps.MIME_TYPE,
            metaProvider = ResourceMeta.class)
    ReadResourceResult widget() {
        return new ReadResourceResult(List.of(new TextResourceContents(
                RESOURCE_URI, McpApps.MIME_TYPE, McpApps.widgetHtml("shopping-list.html"),
                McpApps.resourceMeta())));
    }

    /** Instantiated by reflection by Spring AI: needs a public no-argument constructor. */
    public static final class ToolMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return McpApps.toolMeta(RESOURCE_URI);
        }
    }

    public static final class ResourceMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return McpApps.resourceMeta();
        }
    }
}
