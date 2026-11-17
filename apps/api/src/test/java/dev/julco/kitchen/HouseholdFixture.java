package dev.julco.kitchen;

import dev.julco.kitchen.appliance.Appliance;
import dev.julco.kitchen.appliance.ApplianceMode;
import dev.julco.kitchen.appliance.ApplianceSpec;
import dev.julco.kitchen.common.Aisle;
import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.export.PdfExport;
import dev.julco.kitchen.household.Household;
import dev.julco.kitchen.household.HouseholdInvitation;
import dev.julco.kitchen.household.HouseholdMember;
import dev.julco.kitchen.household.MemberRole;
import dev.julco.kitchen.menu.Meal;
import dev.julco.kitchen.menu.Menu;
import dev.julco.kitchen.menu.MenuItem;
import dev.julco.kitchen.pantry.PantryItem;
import dev.julco.kitchen.pantry.PantrySource;
import dev.julco.kitchen.profile.HouseholdProfile;
import dev.julco.kitchen.profile.PersonProfile;
import dev.julco.kitchen.recipe.Ingredient;
import dev.julco.kitchen.recipe.Recipe;
import dev.julco.kitchen.recipe.RecipeRating;
import dev.julco.kitchen.recipe.RecipeStep;
import dev.julco.kitchen.shopping.ShoppingList;
import dev.julco.kitchen.shopping.ShoppingListItem;
import dev.julco.kitchen.user.AppUser;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * A complete household with one row in every household table, persisted through JPA. Used by the
 * A/B isolation tests: two fixtures, and nothing of one may ever be visible from the other.
 */
public record HouseholdFixture(
        UUID userId,
        UUID householdId,
        UUID invitationId,
        UUID applianceId,
        UUID recipeId,
        UUID ratingId,
        UUID menuId,
        UUID menuItemId,
        UUID shoppingListId,
        UUID shoppingListItemId,
        UUID pantryItemId,
        UUID pdfExportId) {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static HouseholdFixture create(EntityManager em, String name) {
        AppUser user = persist(em, new AppUser(name.toLowerCase() + "-" + UUID.randomUUID() + "@example.test", name, Language.FR));
        Household household = persist(em, new Household("Household " + name));
        UUID householdId = household.getId();
        persist(em, new HouseholdMember(householdId, user.getId(), MemberRole.OWNER));
        persist(em, new PersonProfile(user.getId()));
        persist(em, new HouseholdProfile(householdId));
        Instant inAWeek = Instant.now().plus(7, ChronoUnit.DAYS);
        HouseholdInvitation invitation =
                persist(em, new HouseholdInvitation(householdId, randomHash(), user.getId(), inAWeek));

        Appliance appliance = persist(em, new Appliance(householdId, "Speedi", "Ninja Speedi", new ApplianceSpec(
                List.of(new ApplianceMode("air_fry", 150, 210, null)), "5.7 L", null)));
        Recipe recipe = persist(em, new Recipe(
                householdId,
                appliance.getId(),
                user.getId(),
                "Chicken " + name,
                (short) 4,
                List.of(new Ingredient("chicken", new BigDecimal("600"), "g", Aisle.MEAT_FISH, null)),
                List.of(new RecipeStep("Air fry the chicken", 20, "air_fry", 200)),
                Language.FR));
        RecipeRating rating = persist(em, new RecipeRating(householdId, recipe.getId(), 1, user.getId(), (short) 8, null, null, Instant.now()));

        LocalDate monday = LocalDate.of(2026, 11, 16);
        Menu menu = persist(em, new Menu(householdId, "Week " + name, monday, monday.plusDays(6), user.getId()));
        MenuItem menuItem = persist(em, new MenuItem(householdId, menu.getId(), monday, Meal.DINNER, recipe.getId(), (short) 4));
        ShoppingList list = persist(em, new ShoppingList(householdId, menu.getId(), "List " + name, user.getId()));
        ShoppingListItem item = persist(em, new ShoppingListItem(
                householdId, list.getId(), "chicken", new BigDecimal("600"), "g", Aisle.MEAT_FISH, 0));
        PantryItem pantryItem = persist(em, new PantryItem(householdId, "rice", "half a bag", Aisle.DRY_GOODS, PantrySource.MANUAL));
        PdfExport export = persist(em, new PdfExport(householdId, menu.getId(), user.getId(), randomHash(), Language.FR, inAWeek));

        em.flush();
        em.clear();
        return new HouseholdFixture(
                user.getId(),
                householdId,
                invitation.getId(),
                appliance.getId(),
                recipe.getId(),
                rating.getId(),
                menu.getId(),
                menuItem.getId(),
                list.getId(),
                item.getId(),
                pantryItem.getId(),
                export.getId());
    }

    public static AppUser createUser(EntityManager em, String name) {
        AppUser user = persist(em, new AppUser(name.toLowerCase() + "-" + UUID.randomUUID() + "@example.test", name, Language.NL));
        em.flush();
        return user;
    }

    private static <T> T persist(EntityManager em, T entity) {
        em.persist(entity);
        return entity;
    }

    private static byte[] randomHash() {
        byte[] hash = new byte[32];
        RANDOM.nextBytes(hash);
        return hash;
    }
}
