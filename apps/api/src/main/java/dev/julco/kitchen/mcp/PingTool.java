package dev.julco.kitchen.mcp;

import dev.julco.kitchen.auth.CurrentUsers;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.household.HouseholdSummary;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * Phase 4 tool: proves that an AI client is connected and that its token resolves to the right
 * person and household. Thin adapter: the household comes from the services, the person from the
 * access token.
 */
@Component
class PingTool {

    private final CurrentUsers currentUsers;
    private final HouseholdService households;

    PingTool(CurrentUsers currentUsers, HouseholdService households) {
        this.currentUsers = currentUsers;
        this.households = households;
    }

    @McpTool(
            name = "ping",
            description = "Checks the connection to Kitchen, the household's cooking memory. "
                    + "Returns the name of the connected person's household. Takes no arguments.")
    String ping() {
        return currentUsers.currentMember()
                .map(this::connected)
                .orElse("Kitchen is connected, but this person does not belong to a household yet. "
                        + "They can create one or join one with an invitation link on the Kitchen website.");
    }

    private String connected(CurrentMember member) {
        HouseholdSummary household = households.getHousehold(member);
        return "Kitchen is connected. Household: " + household.name() + ".";
    }
}
