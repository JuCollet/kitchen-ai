package dev.julco.kitchen.recipe;

import dev.julco.kitchen.appliance.ApplianceMode;
import dev.julco.kitchen.appliance.ApplianceService;
import dev.julco.kitchen.appliance.ApplianceView;
import dev.julco.kitchen.common.Aisle;
import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.household.MemberSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * The household's recipe book: recipes, ratings and search. Every read and write is filtered by
 * the current member's household (CLAUDE.md rule 1). A recipe linked to an appliance only uses
 * modes of its confirmed spec sheet (CLAUDE.md rule 5).
 */
@Service
@Validated
@Transactional
public class RecipeService {

    private final RecipeRepository recipes;
    private final RecipeRatingRepository ratings;
    private final RecipeSearchQuery searchQuery;
    private final ApplianceService appliances;
    private final HouseholdService households;

    public RecipeService(
            RecipeRepository recipes,
            RecipeRatingRepository ratings,
            RecipeSearchQuery searchQuery,
            ApplianceService appliances,
            HouseholdService households) {
        this.recipes = recipes;
        this.ratings = ratings;
        this.searchQuery = searchQuery;
        this.appliances = appliances;
        this.households = households;
    }

    /** Creates a recipe, or edits one ({@code id} set): an edit is a new version. */
    public RecipeDetail save(@NotNull CurrentMember currentMember, @NotNull @Valid RecipeInput input) {
        RecipeContent content = content(currentMember, input);
        Recipe recipe;
        if (input.id() == null) {
            recipe = new Recipe(
                    currentMember.householdId(),
                    content.applianceId(),
                    currentMember.userId(),
                    content.title(),
                    content.servings(),
                    content.ingredients(),
                    content.steps(),
                    content.language());
            recipe.apply(content);
        } else {
            recipe = find(currentMember, input.id());
            recipe.edit(content);
        }
        return detail(currentMember, recipes.save(recipe));
    }

    @Transactional(readOnly = true)
    public RecipeDetail get(@NotNull CurrentMember currentMember, @NotNull UUID recipeId) {
        return detail(currentMember, find(currentMember, recipeId));
    }

    @Transactional(readOnly = true)
    public RecipeSearchPage search(@NotNull CurrentMember currentMember, @NotNull @Valid RecipeSearch criteria) {
        return searchQuery.search(currentMember.householdId(), currentMember.userId(), criteria);
    }

    /** Adds a rating to the history: each time the recipe is cooked, the person may rate it again. */
    public RatingView rate(@NotNull CurrentMember currentMember, @NotNull @Valid RatingInput input) {
        Recipe recipe = find(currentMember, input.recipeId());
        Instant cookedAt = input.cookedOn() == null
                ? Instant.now()
                : input.cookedOn().atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
        RecipeRating rating = ratings.save(new RecipeRating(
                currentMember.householdId(),
                recipe.getId(),
                recipe.getVersion(),
                currentMember.userId(),
                input.score().shortValue(),
                blankToNull(input.comment()),
                blankToNull(input.adjustments()),
                cookedAt));
        return view(rating, currentMember, memberNames(currentMember));
    }

    private Recipe find(CurrentMember currentMember, UUID recipeId) {
        return recipes.findByIdAndHouseholdId(recipeId, currentMember.householdId())
                .orElseThrow(NotFoundException::new);
    }

    private RecipeContent content(CurrentMember currentMember, RecipeInput input) {
        List<Ingredient> ingredients = input.ingredients().stream()
                .map(i -> new Ingredient(
                        i.name().strip(),
                        i.quantity(),
                        blankToNull(i.unit()),
                        i.aisle() == null ? Aisle.OTHER : Aisle.fromCode(i.aisle()),
                        blankToNull(i.note())))
                .toList();
        List<RecipeStep> steps = input.steps().stream()
                .map(s -> new RecipeStep(s.text().strip(), s.durationMin(), blankToNull(s.applianceMode()), s.temperature()))
                .toList();
        checkApplianceModes(currentMember, input.applianceId(), steps);
        return new RecipeContent(
                input.applianceId(),
                input.title().strip(),
                blankToNull(input.summary()),
                input.servings().shortValue(),
                toShort(input.activeMinutes()),
                toShort(input.totalMinutes()),
                ingredients,
                steps,
                lowercaseUnique(input.tags()),
                input.nutritionEstimate(),
                unique(input.benefits()),
                input.language() == null ? currentMember.language() : input.language(),
                blankToNull(input.source()));
    }

    /**
     * CLAUDE.md rule 5: the sheet confirmed by the household is the only source of truth. The
     * errors list the real modes, so that the AI can fix the recipe instead of guessing again.
     */
    private void checkApplianceModes(CurrentMember currentMember, UUID applianceId, List<RecipeStep> steps) {
        if (applianceId == null) {
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).applianceMode() != null) {
                    throw new InvalidInputException("Step " + (i + 1) + " uses the appliance mode "
                            + steps.get(i).applianceMode() + ", but the recipe is not linked to an appliance: "
                            + "set applianceId, or remove appliance_mode for a classic recipe.");
                }
            }
            return;
        }
        ApplianceView appliance;
        try {
            appliance = appliances.get(currentMember, applianceId);
        } catch (NotFoundException e) {
            throw new InvalidInputException(
                    "Appliance not found in this household: use list_appliances to get the ids.");
        }
        Map<String, ApplianceMode> modes = appliance.spec().modes().stream()
                .collect(Collectors.toMap(ApplianceMode::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        for (int i = 0; i < steps.size(); i++) {
            RecipeStep step = steps.get(i);
            if (step.applianceMode() == null) {
                continue;
            }
            ApplianceMode mode = modes.get(step.applianceMode());
            if (mode == null) {
                throw new InvalidInputException("Step " + (i + 1) + " uses the mode " + step.applianceMode()
                        + ", which is not in the confirmed spec sheet of " + appliance.name() + ". Its modes are: "
                        + String.join(", ", modes.keySet()) + ". Use one of them, or change the step.");
            }
            Integer temperature = step.temperature();
            if (temperature != null
                    && ((mode.tempMin() != null && temperature < mode.tempMin())
                            || (mode.tempMax() != null && temperature > mode.tempMax()))) {
                throw new InvalidInputException("Step " + (i + 1) + ": " + temperature + " °C is outside the range of "
                        + "the mode " + mode.name() + " of " + appliance.name() + " (" + range(mode) + ").");
            }
        }
    }

    private RecipeDetail detail(CurrentMember currentMember, Recipe recipe) {
        Map<UUID, String> names = memberNames(currentMember);
        List<RecipeRating> history =
                ratings.findAllByHouseholdIdAndRecipeIdOrderByCookedAtDescIdDesc(currentMember.householdId(), recipe.getId());
        // Latest rating of each member (the history is sorted latest first).
        Map<UUID, RecipeRating> latest = new LinkedHashMap<>();
        history.forEach(rating -> latest.putIfAbsent(rating.getUserId(), rating));
        RecipeRating mine = latest.get(currentMember.userId());
        BigDecimal householdScore = latest.isEmpty()
                ? null
                : BigDecimal.valueOf(latest.values().stream().mapToInt(RecipeRating::getScore).average().orElseThrow())
                        .setScale(1, RoundingMode.HALF_UP);
        ApplianceView appliance = recipe.getApplianceId() == null
                ? null
                : appliances.get(currentMember, recipe.getApplianceId());
        return new RecipeDetail(
                recipe.getId(),
                recipe.getTitle(),
                recipe.getSummary(),
                recipe.getServings(),
                toInteger(recipe.getActiveMinutes()),
                toInteger(recipe.getTotalMinutes()),
                recipe.getIngredients(),
                recipe.getSteps(),
                recipe.getTags(),
                recipe.getNutritionEstimate(),
                recipe.getBenefits(),
                recipe.getLanguage(),
                recipe.getSource(),
                recipe.getVersion(),
                recipe.getUpdatedAt(),
                appliance,
                mine == null ? null : (int) mine.getScore(),
                householdScore,
                history.stream().map(rating -> view(rating, currentMember, names)).toList());
    }

    private Map<UUID, String> memberNames(CurrentMember currentMember) {
        return households.listMembers(currentMember).stream()
                .collect(Collectors.toMap(MemberSummary::userId, MemberSummary::displayName));
    }

    private static RatingView view(RecipeRating rating, CurrentMember currentMember, Map<UUID, String> names) {
        return new RatingView(
                names.getOrDefault(rating.getUserId(), "?"),
                rating.getUserId().equals(currentMember.userId()),
                rating.getScore(),
                rating.getComment(),
                rating.getAdjustments(),
                rating.getCookedAt(),
                rating.getRecipeVersion());
    }

    private static String range(ApplianceMode mode) {
        return (mode.tempMin() == null ? "?" : mode.tempMin()) + " to " + (mode.tempMax() == null ? "?" : mode.tempMax())
                + " °C";
    }

    private static List<String> lowercaseUnique(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(v -> v.strip().toLowerCase(Locale.ROOT)).distinct().toList();
    }

    private static List<String> unique(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        values.stream().map(String::strip).filter(v -> result.stream().noneMatch(v::equalsIgnoreCase)).forEach(result::add);
        return List.copyOf(result);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    private static Integer toInteger(Short value) {
        return value == null ? null : value.intValue();
    }
}
