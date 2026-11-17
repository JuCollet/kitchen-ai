package dev.julco.kitchen.appliance;

import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.mcp.ToolRunner;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP adapter of {@link ApplianceService}. */
@Component
class ApplianceTools {

    private static final String NOT_FOUND =
            "Appliance not found in this household: use list_appliances to get the ids.";

    private final ApplianceService appliances;
    private final ToolRunner runner;

    ApplianceTools(ApplianceService appliances, ToolRunner runner) {
        this.appliances = appliances;
        this.runner = runner;
    }

    @McpTool(
            name = "list_appliances",
            title = "List the household's appliances",
            description = """
                    Lists the cooking appliances of the connected person's household, each with \
                    its id, its confirmed spec sheet (the only modes and temperature ranges that \
                    may be used in recipes) and its usage preference: default (use it for every \
                    recipe where possible), suggest (may be suggested when relevant), on_request \
                    (only when asked). Returns {appliances: [...]}. Takes no arguments.""")
    CallToolResult listAppliances() {
        return runner.run(NOT_FOUND, member -> new Appliances(appliances.list(member)));
    }

    @McpTool(
            name = "save_appliance",
            title = "Save an appliance and its spec sheet",
            description = """
                    Adds a cooking appliance to the household, or replaces one (pass its id). \
                    Before calling: write its spec sheet (real modes with a lowercase code like \
                    air_fry, temperature range in °C, notes; capacity; remarks), show it to the \
                    person and let them correct it. Only call with confirmedByUser true once they \
                    confirmed it. Never invent a mode: if unsure, ask. The sheet becomes the only \
                    source of truth for this appliance's modes. Returns the saved appliance.""")
    CallToolResult saveAppliance(
            @McpToolParam(required = false, description = "Id of the appliance to replace; omit to add a new one.")
                    UUID id,
            @McpToolParam(description = "Name used by the household, for example 'Air fryer'. Max 100 characters.")
                    String name,
            @McpToolParam(required = false, description = "Brand and model, for example 'Ninja Speedi ON400'.")
                    String brandModel,
            @McpToolParam(description = "Confirmed spec sheet: modes (1 to 30), capacity, remarks.") ApplianceSpec spec,
            @McpToolParam(required = false, description = "default, suggest or on_request. Defaults to suggest for a new appliance.")
                    String usagePreference,
            @McpToolParam(description = "true only if the person read and confirmed the spec sheet.")
                    boolean confirmedByUser) {
        return runner.run(NOT_FOUND, member -> appliances.save(member,
                new ApplianceInput(id, name, brandModel, spec, preference(usagePreference), confirmedByUser)));
    }

    @McpTool(
            name = "set_appliance_preference",
            title = "Set how an appliance is used",
            description = """
                    Changes the permanent usage preference of a household appliance: default \
                    (every recipe uses it whenever possible), suggest (may be suggested when \
                    relevant) or on_request (only when the person asks for it). Returns the \
                    appliance.""")
    CallToolResult setAppliancePreference(
            @McpToolParam(description = "Appliance id, from list_appliances or get_context.") UUID applianceId,
            @McpToolParam(description = "default, suggest or on_request.") String usagePreference) {
        return runner.run(NOT_FOUND, member -> appliances.setUsagePreference(
                member, applianceId, preference(usagePreference)));
    }

    private static UsagePreference preference(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(UsagePreference.values())
                .filter(preference -> preference.code().equals(code.strip().toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("usagePreference must be default, suggest or on_request."));
    }

    /** MCP structured content must be a JSON object, never a bare array. */
    record Appliances(List<ApplianceView> appliances) {}
}
