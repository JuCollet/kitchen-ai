package dev.julco.kitchen.onboarding;

import dev.julco.kitchen.appliance.ApplianceService;
import dev.julco.kitchen.appliance.ApplianceView;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.mcp.ToolRunner;
import dev.julco.kitchen.menu.MenuService;
import dev.julco.kitchen.menu.MenuView;
import dev.julco.kitchen.menu.RecipeToRate;
import dev.julco.kitchen.pantry.PantryService;
import dev.julco.kitchen.pantry.PantrySummary;
import dev.julco.kitchen.profile.HouseholdProfileView;
import dev.julco.kitchen.profile.PersonProfileView;
import dev.julco.kitchen.profile.ProfileService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/** Everything the AI needs at the start of a cooking conversation, in one call. */
@Component
class ContextTool {

    private final ProfileService profiles;
    private final HouseholdService households;
    private final ApplianceService appliances;
    private final OnboardingService onboarding;
    private final MenuService menus;
    private final PantryService pantry;
    private final ToolRunner runner;

    ContextTool(
            ProfileService profiles,
            HouseholdService households,
            ApplianceService appliances,
            OnboardingService onboarding,
            MenuService menus,
            PantryService pantry,
            ToolRunner runner) {
        this.profiles = profiles;
        this.households = households;
        this.appliances = appliances;
        this.onboarding = onboarding;
        this.menus = menus;
        this.pantry = pantry;
        this.runner = runner;
    }

    @McpTool(
            name = "get_context",
            title = "Get the Kitchen context",
            description = """
                    Call this first in every conversation about cooking, meals, recipes, \
                    groceries or appliances. Returns the connected person's profile and \
                    language, their household (profile, and the diet, allergies and dislikes of \
                    every member, whose union household meals must respect), the household's \
                    appliances with their confirmed spec sheets and usage preferences, the \
                    onboarding state (if onboarding.nextQuestions is not empty, ask those \
                    questions first, in order), the latest menu, the recipes served in the last \
                    two weeks that this person has not rated yet (recipesToRate), and a summary \
                    of the pantry. Takes no arguments.""")
    CallToolResult getContext() {
        return runner.run("Household not found.", member -> new Context(
                profiles.getMyProfile(member),
                new Household(
                        households.getHousehold(member).name(),
                        profiles.getHouseholdProfile(member),
                        profiles.listMemberConstraints(member).stream()
                                .map(c -> new Member(c.displayName(), c.diet(), c.allergies(), c.dislikes()))
                                .toList()),
                appliances.list(member),
                onboarding.state(member),
                menus.latest(member).orElse(null),
                menus.recipesToRate(member),
                pantry.summary(member)));
    }

    record Context(
            PersonProfileView me,
            Household household,
            List<ApplianceView> appliances,
            OnboardingState onboarding,
            MenuView latestMenu,
            List<RecipeToRate> recipesToRate,
            PantrySummary pantry) {}

    record Household(String name, HouseholdProfileView profile, List<Member> members) {}

    record Member(String displayName, String diet, List<String> allergies, List<String> dislikes) {}
}
