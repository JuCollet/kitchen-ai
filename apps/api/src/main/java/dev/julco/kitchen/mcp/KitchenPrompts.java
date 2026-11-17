package dev.julco.kitchen.mcp;

import dev.julco.kitchen.auth.CurrentUsers;
import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.household.CurrentMember;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * The four one-click actions (MCP prompts). A prompt returns the message the person "sends" to
 * their AI: it is visible to them, so it is written in their language (messages*.properties,
 * CLAUDE.md rule 10), and it spells out which tools to call, in which order.
 *
 * <p>The titles and descriptions below are in English only: in a stateless server the prompt list
 * is built once at startup, without knowing who asks for it. Arguments are free text typed by the
 * person, inserted into their own message: only trimmed and bounded here.
 */
@Component
class KitchenPrompts {

    private static final int MAX_ARGUMENT_LENGTH = 200;
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 14;

    private final CurrentUsers currentUsers;
    private final MessageSource messages;

    KitchenPrompts(CurrentUsers currentUsers, MessageSource messages) {
        this.currentUsers = currentUsers;
        this.messages = messages;
    }

    @McpPrompt(
            name = "weekly_menu",
            title = "Weekly menu",
            description = "Plan the household's meals for a period from its recipe book, then build the shopping list.")
    GetPromptResult weeklyMenu(
            @McpArg(name = "start", description = "First day, for example 2026-11-23 or 'next Monday'. Default: next Monday.")
                    String start,
            @McpArg(name = "days", description = "Number of days, 1 to 14. Default: 7.") String days,
            @McpArg(name = "appliances", description = "Appliances for this period, for example 'only the Cookeo'.")
                    String appliances) {
        Locale locale = locale();
        String startText = clean(start);
        if (startText == null) {
            LocalDate nextMonday = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            startText = nextMonday.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                    + " (" + nextMonday + ")";
        }
        return prompt("weekly_menu", locale, startText, String.valueOf(days(days)),
                optional("prompt.weekly_menu.appliances", appliances, locale));
    }

    @McpPrompt(
            name = "recipe_of_the_day",
            title = "Recipe of the day",
            description = "A recipe suggestion for today, from the household's book, pantry and appliances.")
    GetPromptResult recipeOfTheDay(
            @McpArg(name = "craving", description = "What the person feels like eating, if anything.") String craving) {
        Locale locale = locale();
        return prompt("recipe_of_the_day", locale, optional("prompt.recipe_of_the_day.craving", craving, locale));
    }

    @McpPrompt(
            name = "rate_a_recipe",
            title = "Rate a recipe",
            description = "Rate a recipe after cooking it, note what to change next time, and update the pantry.")
    GetPromptResult rateARecipe(
            @McpArg(name = "recipe", description = "The recipe that was cooked, if the person names it.") String recipe) {
        Locale locale = locale();
        return prompt("rate_a_recipe", locale, optional("prompt.rate_a_recipe.recipe", recipe, locale));
    }

    @McpPrompt(
            name = "clear_the_fridge",
            title = "Clear the fridge",
            description = "A recipe that uses up what is left in the household's pantry.")
    GetPromptResult clearTheFridge(
            @McpArg(name = "ingredients", description = "Other ingredients to use up, not yet in the pantry.")
                    String ingredients) {
        Locale locale = locale();
        return prompt("clear_the_fridge", locale, optional("prompt.clear_the_fridge.ingredients", ingredients, locale));
    }

    /** The person's language; English for someone without a household yet (the tools tell them how to join one). */
    private Locale locale() {
        Language language = currentUsers.currentMember().map(CurrentMember::language).orElse(Language.EN);
        return Locale.of(language.code());
    }

    /**
     * Every message key is used with at least one argument, so Spring always applies MessageFormat
     * and a doubled quote ('') becomes a single one.
     */
    private GetPromptResult prompt(String name, Locale locale, Object... args) {
        String text = messages.getMessage("prompt." + name, args, locale);
        return new GetPromptResult(null, List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    /** A sentence inserted only when the person gave the argument, else nothing. */
    private String optional(String key, String value, Locale locale) {
        String cleaned = clean(value);
        return cleaned == null ? "" : messages.getMessage(key, new Object[] {cleaned}, locale);
    }

    private static int days(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return DEFAULT_DAYS;
        }
        try {
            return Math.clamp(Integer.parseInt(cleaned), 1, MAX_DAYS);
        } catch (NumberFormatException e) {
            return DEFAULT_DAYS;
        }
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() > MAX_ARGUMENT_LENGTH ? stripped.substring(0, MAX_ARGUMENT_LENGTH) : stripped;
    }
}
