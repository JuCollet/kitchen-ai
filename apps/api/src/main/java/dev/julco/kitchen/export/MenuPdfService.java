package dev.julco.kitchen.export;

import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.export.MenuDocument.AisleGroup;
import dev.julco.kitchen.export.MenuDocument.Day;
import dev.julco.kitchen.export.MenuDocument.IngredientLine;
import dev.julco.kitchen.export.MenuDocument.MealLine;
import dev.julco.kitchen.export.MenuDocument.Nutrition;
import dev.julco.kitchen.export.MenuDocument.RecipeSheet;
import dev.julco.kitchen.export.MenuDocument.Shopping;
import dev.julco.kitchen.export.MenuDocument.ShoppingLine;
import dev.julco.kitchen.export.MenuDocument.StepLine;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.menu.MenuService;
import dev.julco.kitchen.menu.MenuView;
import dev.julco.kitchen.menu.MenuView.MenuMeal;
import dev.julco.kitchen.recipe.Ingredient;
import dev.julco.kitchen.recipe.RecipeDetail;
import dev.julco.kitchen.recipe.RecipeService;
import dev.julco.kitchen.recipe.RecipeStep;
import dev.julco.kitchen.shopping.ShoppingListService;
import dev.julco.kitchen.shopping.ShoppingListView;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * The menu PDF as HTML: meal plan, shopping list by aisle, one sheet per recipe with quantities for
 * the menu's portions. Reads through the other features' services, so every read is filtered by
 * the member's household like any other (CLAUDE.md rule 1). Content written by people goes into
 * the template as text only (th:text), never as HTML (rule 6).
 */
@Service
@Validated
@Transactional(readOnly = true)
public class MenuPdfService {

    private final MenuService menus;
    private final RecipeService recipes;
    private final ShoppingListService shoppingLists;
    private final MessageSource messages;
    private final ITemplateEngine templates;

    public MenuPdfService(
            MenuService menus,
            RecipeService recipes,
            ShoppingListService shoppingLists,
            MessageSource messages,
            ITemplateEngine templates) {
        this.menus = menus;
        this.recipes = recipes;
        this.shoppingLists = shoppingLists;
        this.messages = messages;
        this.templates = templates;
    }

    public String html(@NotNull CurrentMember currentMember, @NotNull UUID menuId, @NotNull Language language) {
        Locale locale = Locale.of(language.code());
        Context context = new Context(locale);
        context.setVariable("doc", document(currentMember, menuId, locale));
        return templates.process("pdf/menu", context);
    }

    MenuDocument document(CurrentMember currentMember, UUID menuId, Locale locale) {
        MenuView menu = menus.get(currentMember, menuId);
        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", locale);
        DateTimeFormatter shortFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", locale);

        Map<LocalDate, List<MenuMeal>> byDay = menu.meals().stream()
                .collect(Collectors.groupingBy(MenuMeal::servedOn, LinkedHashMap::new, Collectors.toList()));
        List<Day> days = byDay.entrySet().stream()
                .map(day -> new Day(capitalize(day.getKey().format(dayFormat), locale), day.getValue().stream()
                        .map(meal -> new MealLine(
                                message("pdf.meal." + meal.meal().code(), locale), meal.recipeTitle(), meal.servings()))
                        .toList()))
                .toList();

        // One sheet per recipe and number of portions: a recipe served twice for 4 has one sheet.
        Map<String, MenuMeal> sheets = new LinkedHashMap<>();
        menu.meals().forEach(meal -> sheets.putIfAbsent(meal.recipeId() + "/" + meal.servings(), meal));
        List<RecipeSheet> recipeSheets = sheets.values().stream()
                .map(meal -> sheet(recipes.get(currentMember, meal.recipeId()), meal.servings(), locale))
                .toList();

        Shopping shopping = shoppingLists.latestForMenu(currentMember, menu.id())
                .map(list -> shopping(list, locale))
                .orElse(null);

        String period = menu.startsOn().equals(menu.endsOn())
                ? menu.startsOn().format(shortFormat)
                : menu.startsOn().format(shortFormat) + " – " + menu.endsOn().format(shortFormat);
        return new MenuDocument(menu.title(), period, locale.getLanguage(), days, shopping, recipeSheets);
    }

    private RecipeSheet sheet(RecipeDetail recipe, int servings, Locale locale) {
        BigDecimal factor = BigDecimal.valueOf(servings)
                .divide(BigDecimal.valueOf(recipe.servings()), 6, RoundingMode.HALF_UP);
        List<IngredientLine> ingredients = recipe.ingredients().stream()
                .map(ingredient -> new IngredientLine(
                        quantity(ingredient, factor, locale), ingredient.name(), ingredient.note()))
                .toList();
        List<StepLine> steps = recipe.steps().stream().map(this::step).toList();
        Nutrition nutrition = recipe.nutritionEstimate() == null ? null : new Nutrition(
                recipe.nutritionEstimate().caloriesKcal(),
                recipe.nutritionEstimate().proteinG(),
                recipe.nutritionEstimate().carbsG(),
                recipe.nutritionEstimate().fatG(),
                recipe.nutritionEstimate().fiberG());
        return new RecipeSheet(
                recipe.title(),
                recipe.summary(),
                servings,
                recipe.activeMinutes(),
                recipe.totalMinutes(),
                recipe.appliance() == null ? null : recipe.appliance().name(),
                ingredients,
                steps,
                nutrition,
                recipe.benefits(),
                recipe.source());
    }

    private StepLine step(RecipeStep step) {
        List<String> settings = new ArrayList<>();
        if (step.applianceMode() != null) {
            settings.add(step.applianceMode());
        }
        if (step.temperature() != null) {
            settings.add(step.temperature() + " °C");
        }
        if (step.durationMin() != null) {
            settings.add(step.durationMin() + " min");
        }
        return new StepLine(step.text(), settings.isEmpty() ? null : String.join(" · ", settings));
    }

    private Shopping shopping(ShoppingListView list, Locale locale) {
        Map<String, List<ShoppingLine>> byAisle = new LinkedHashMap<>();
        for (ShoppingListView.Item item : list.items()) {
            String aisle = message("pdf.aisle." + item.aisle().code(), locale);
            byAisle.computeIfAbsent(aisle, ignored -> new ArrayList<>()).add(new ShoppingLine(
                    item.name(), amount(item.quantity(), item.unit(), locale), item.checked()));
        }
        return new Shopping(list.title(), byAisle.entrySet().stream()
                .map(aisle -> new AisleGroup(aisle.getKey(), aisle.getValue()))
                .toList());
    }

    private static String quantity(Ingredient ingredient, BigDecimal factor, Locale locale) {
        return ingredient.quantity() == null
                ? null
                : amount(ingredient.quantity().multiply(factor), ingredient.unit(), locale);
    }

    /** "1,5 l", "600 g", "3": large amounts are rounded, small ones keep a decimal or two. */
    private static String amount(BigDecimal quantity, String unit, Locale locale) {
        if (quantity == null) {
            return null;
        }
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(quantity.compareTo(BigDecimal.TEN) >= 0 ? 0 : 2);
        String number = format.format(quantity);
        return unit == null ? number : number + " " + unit;
    }

    private String message(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }

    private static String capitalize(String text, Locale locale) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }
}
