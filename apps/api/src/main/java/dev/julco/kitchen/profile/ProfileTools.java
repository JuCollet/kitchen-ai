package dev.julco.kitchen.profile;

import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.mcp.ToolRunner;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP adapter of {@link ProfileService}: no rule here, only argument mapping. */
@Component
class ProfileTools {

    private final ProfileService profiles;
    private final ToolRunner runner;

    ProfileTools(ProfileService profiles, ToolRunner runner) {
        this.profiles = profiles;
        this.runner = runner;
    }

    @McpTool(
            name = "update_my_profile",
            title = "Update my Kitchen profile",
            description = """
                    Updates the connected person's own profile in Kitchen: diet, allergies, \
                    dislikes, notes, language. Only pass the fields to change; an empty text or \
                    list clears a field. Lists replace the previous ones, so send the complete \
                    list. Allergies are health data: they are refused unless the person allowed \
                    Kitchen to store them on the Kitchen website, and they are shared with the \
                    other household members so that household menus are safe. Set \
                    onboardingComplete to true once the personal onboarding questions have all \
                    been answered or skipped. Returns the updated profile.""")
    CallToolResult updateMyProfile(
            @McpToolParam(required = false, description = "Diet, for example vegetarian, or 'none'. Max 200 characters.")
                    String diet,
            @McpToolParam(required = false, description = "Complete list of allergies and intolerances. Max 30 items of 100 characters.")
                    List<String> allergies,
            @McpToolParam(required = false, description = "Complete list of disliked foods or dishes. Max 50 items of 100 characters.")
                    List<String> dislikes,
            @McpToolParam(required = false, description = "Other personal cooking notes. Max 2000 characters.")
                    String notes,
            @McpToolParam(required = false, description = "Language of the person: fr, nl or en.") String language,
            @McpToolParam(required = false, description = "true once the personal onboarding is done.")
                    Boolean onboardingComplete) {
        return runner.run("Profile not found.", member -> profiles.updateMyProfile(member, new PersonProfileUpdate(
                diet, allergies, dislikes, notes, language(language), onboardingComplete)));
    }

    @McpTool(
            name = "update_household_profile",
            title = "Update the household profile",
            description = """
                    Updates the profile shared by the whole household in Kitchen: table size, \
                    cooking goals, budget, maximum hands-on cooking time on weekdays, staples \
                    always at home (never put on shopping lists), notes. Only pass the fields to \
                    change; an empty text or list clears a field, and lists replace the previous \
                    ones. Set onboardingComplete to true once the household onboarding questions \
                    have all been answered or skipped. Returns the updated profile.""")
    CallToolResult updateHouseholdProfile(
            @McpToolParam(required = false, description = "Number of people usually at the table, 1 to 20.")
                    Integer tableSize,
            @McpToolParam(required = false, description = "Cooking goals, for example 'eat healthier', 'save time'. Max 10 items.")
                    List<String> goals,
            @McpToolParam(required = false, description = "Details about the goals. Max 1000 characters.")
                    String goalsNote,
            @McpToolParam(required = false, description = "Food budget in the person's words, for example '120 EUR per week'.")
                    String budget,
            @McpToolParam(required = false, description = "Maximum hands-on cooking time on weekdays, in minutes, 1 to 600.")
                    Integer maxActiveMinutes,
            @McpToolParam(required = false, description = "Staples always at home. Max 50 items.") List<String> staples,
            @McpToolParam(required = false, description = "Other household notes. Max 2000 characters.") String notes,
            @McpToolParam(required = false, description = "true once the household onboarding is done.")
                    Boolean onboardingComplete) {
        return runner.run("Household not found.", member -> profiles.updateHouseholdProfile(member,
                new HouseholdProfileUpdate(tableSize, goals, goalsNote, budget, maxActiveMinutes, staples, notes,
                        onboardingComplete)));
    }

    private static Language language(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(Language.values())
                .filter(language -> language.code().equals(code.strip().toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("language must be fr, nl or en."));
    }
}
