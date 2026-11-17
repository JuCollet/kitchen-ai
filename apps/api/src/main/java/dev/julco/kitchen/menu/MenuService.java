package dev.julco.kitchen.menu;

import dev.julco.kitchen.appliance.ApplianceService;
import dev.julco.kitchen.appliance.ApplianceView;
import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.menu.MenuView.ApplianceRef;
import dev.julco.kitchen.menu.MenuView.MenuMeal;
import dev.julco.kitchen.menu.MenuView.PeriodAppliances;
import dev.julco.kitchen.recipe.RecipeBrief;
import dev.julco.kitchen.recipe.RecipeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * The household's menus: meals of existing recipes over a period, with the appliances required or
 * excluded for that period. Every read and write is filtered by the current member's household
 * (CLAUDE.md rule 1).
 */
@Service
@Validated
@Transactional
public class MenuService {

    /** How far back get_context looks for meals the person has not rated yet. */
    static final int RATING_WINDOW_DAYS = 14;

    private static final int MAX_PERIOD_DAYS = 31;

    private final MenuRepository menus;
    private final MenuItemRepository items;
    private final RecipesToRateQuery recipesToRate;
    private final RecipeService recipes;
    private final ApplianceService appliances;

    public MenuService(
            MenuRepository menus,
            MenuItemRepository items,
            RecipesToRateQuery recipesToRate,
            RecipeService recipes,
            ApplianceService appliances) {
        this.menus = menus;
        this.items = items;
        this.recipesToRate = recipesToRate;
        this.recipes = recipes;
        this.appliances = appliances;
    }

    /** Creates a menu, or replaces one entirely ({@code id} set): title, period, appliances and meals. */
    public MenuView save(@NotNull CurrentMember currentMember, @NotNull @Valid MenuInput input) {
        checkPeriod(input);
        Map<UUID, ApplianceView> householdAppliances = applianceMap(currentMember);
        List<UUID> required = distinct(input.requiredApplianceIds());
        List<UUID> excluded = distinct(input.excludedApplianceIds());
        checkAppliances(householdAppliances, required, excluded);
        Map<UUID, RecipeBrief> briefs = recipes.briefs(
                currentMember, input.items().stream().map(MenuItemInput::recipeId).collect(Collectors.toSet()));
        checkItems(input, briefs, householdAppliances, Set.copyOf(excluded));

        ApplianceOverrides overrides = new ApplianceOverrides(required, excluded);
        Menu menu;
        if (input.id() == null) {
            menu = new Menu(currentMember.householdId(), input.title().strip(), input.startsOn(), input.endsOn(),
                    currentMember.userId());
        } else {
            menu = find(currentMember, input.id());
            items.deleteAllByHouseholdIdAndMenuId(currentMember.householdId(), menu.getId());
        }
        menu.replace(input.title().strip(), input.startsOn(), input.endsOn(), overrides);
        menu = menus.save(menu);
        for (MenuItemInput item : input.items()) {
            items.save(new MenuItem(currentMember.householdId(), menu.getId(), item.servedOn(),
                    Meal.fromCode(item.meal()), item.recipeId(), item.servings().shortValue()));
        }
        return view(currentMember, menu, householdAppliances);
    }

    @Transactional(readOnly = true)
    public MenuView get(@NotNull CurrentMember currentMember, @NotNull UUID menuId) {
        return view(currentMember, find(currentMember, menuId), applianceMap(currentMember));
    }

    /** The menu that starts last (the most recently created one if several start the same day). */
    @Transactional(readOnly = true)
    public Optional<MenuView> latest(@NotNull CurrentMember currentMember) {
        return menus.findFirstByHouseholdIdOrderByStartsOnDescCreatedAtDesc(currentMember.householdId())
                .map(menu -> view(currentMember, menu, applianceMap(currentMember)));
    }

    /** Recipes served in the last {@value #RATING_WINDOW_DAYS} days, up to today, that the person has not rated since. */
    @Transactional(readOnly = true)
    public List<RecipeToRate> recipesToRate(@NotNull CurrentMember currentMember) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return recipesToRate.find(currentMember.householdId(), currentMember.userId(),
                today.minusDays(RATING_WINDOW_DAYS), today);
    }

    private Menu find(CurrentMember currentMember, UUID menuId) {
        return menus.findByIdAndHouseholdId(menuId, currentMember.householdId()).orElseThrow(NotFoundException::new);
    }

    private static void checkPeriod(MenuInput input) {
        long days = ChronoUnit.DAYS.between(input.startsOn(), input.endsOn());
        if (days < 0 || days > MAX_PERIOD_DAYS) {
            throw new InvalidInputException("endsOn must be on or after startsOn, and at most " + MAX_PERIOD_DAYS
                    + " days later.");
        }
    }

    private static void checkAppliances(Map<UUID, ApplianceView> householdAppliances, List<UUID> required, List<UUID> excluded) {
        for (UUID id : concat(required, excluded)) {
            if (!householdAppliances.containsKey(id)) {
                throw new InvalidInputException("Appliance " + id
                        + " not found in this household: use the appliance ids from get_context or list_appliances.");
            }
        }
        for (UUID id : required) {
            if (excluded.contains(id)) {
                throw new InvalidInputException("The appliance " + householdAppliances.get(id).name()
                        + " cannot be both required and excluded for the period.");
            }
        }
    }

    /**
     * Every meal uses a recipe of the household's book, on a day of the period, and no recipe uses
     * an appliance excluded for the period. Required appliances stay guidance for the AI: "only
     * the Cookeo this week" must not forbid a salad.
     */
    private static void checkItems(
            MenuInput input,
            Map<UUID, RecipeBrief> briefs,
            Map<UUID, ApplianceView> householdAppliances,
            Set<UUID> excluded) {
        for (int i = 0; i < input.items().size(); i++) {
            MenuItemInput item = input.items().get(i);
            String where = "items[" + i + "]: ";
            RecipeBrief recipe = briefs.get(item.recipeId());
            if (recipe == null) {
                throw new InvalidInputException(where + "recipe " + item.recipeId() + " not found in this household's "
                        + "book. Use search_recipes to get the ids, or save the recipe with save_recipe first.");
            }
            if (item.servedOn().isBefore(input.startsOn()) || item.servedOn().isAfter(input.endsOn())) {
                throw new InvalidInputException(where + item.servedOn() + " is outside the menu's period ("
                        + input.startsOn() + " to " + input.endsOn() + ").");
            }
            if (recipe.applianceId() != null && excluded.contains(recipe.applianceId())) {
                throw new InvalidInputException(where + "the recipe \"" + recipe.title() + "\" uses the appliance "
                        + householdAppliances.get(recipe.applianceId()).name() + ", which is excluded for this period. "
                        + "Choose another recipe, or remove the appliance from excludedApplianceIds.");
            }
        }
    }

    private MenuView view(CurrentMember currentMember, Menu menu, Map<UUID, ApplianceView> householdAppliances) {
        List<MenuItem> menuItems = items.findAllByHouseholdIdAndMenuIdOrderByServedOn(currentMember.householdId(), menu.getId());
        Map<UUID, RecipeBrief> briefs = recipes.briefs(
                currentMember, menuItems.stream().map(MenuItem::getRecipeId).collect(Collectors.toSet()));
        List<MenuMeal> meals = menuItems.stream()
                .sorted(Comparator.comparing(MenuItem::getServedOn).thenComparing(MenuItem::getMeal))
                .map(item -> {
                    RecipeBrief recipe = briefs.get(item.getRecipeId());
                    return new MenuMeal(item.getServedOn(), item.getMeal(), item.getRecipeId(),
                            recipe == null ? null : recipe.title(),
                            recipe == null ? null : recipe.applianceId(),
                            item.getServings());
                })
                .toList();
        ApplianceOverrides overrides = menu.getApplianceOverrides();
        return new MenuView(
                menu.getId(),
                menu.getTitle(),
                menu.getStartsOn(),
                menu.getEndsOn(),
                new PeriodAppliances(refs(overrides.required(), householdAppliances), refs(overrides.excluded(), householdAppliances)),
                meals,
                menu.getCreatedAt());
    }

    private Map<UUID, ApplianceView> applianceMap(CurrentMember currentMember) {
        return appliances.list(currentMember).stream().collect(Collectors.toMap(ApplianceView::id, Function.identity()));
    }

    /** Appliances of the household only: an id that no longer matches one is left out. */
    private static List<ApplianceRef> refs(List<UUID> ids, Map<UUID, ApplianceView> householdAppliances) {
        return ids.stream()
                .filter(householdAppliances::containsKey)
                .map(id -> new ApplianceRef(id, householdAppliances.get(id).name()))
                .toList();
    }

    private static List<UUID> distinct(List<UUID> ids) {
        return ids == null ? List.of() : List.copyOf(new LinkedHashSet<>(ids));
    }

    private static List<UUID> concat(List<UUID> a, List<UUID> b) {
        return Stream.concat(a.stream(), b.stream()).toList();
    }
}
