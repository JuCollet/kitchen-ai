package dev.julco.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.julco.kitchen.appliance.Appliance;
import dev.julco.kitchen.appliance.ApplianceRepository;
import dev.julco.kitchen.export.PdfExportRepository;
import dev.julco.kitchen.household.HouseholdInvitation;
import dev.julco.kitchen.household.HouseholdInvitationRepository;
import dev.julco.kitchen.household.HouseholdMemberRepository;
import dev.julco.kitchen.household.MemberSummary;
import dev.julco.kitchen.menu.MenuItem;
import dev.julco.kitchen.menu.MenuItemRepository;
import dev.julco.kitchen.menu.MenuRepository;
import dev.julco.kitchen.pantry.PantryItem;
import dev.julco.kitchen.pantry.PantryItemRepository;
import dev.julco.kitchen.profile.HouseholdProfileRepository;
import dev.julco.kitchen.profile.PersonProfileRepository;
import dev.julco.kitchen.recipe.Recipe;
import dev.julco.kitchen.recipe.RecipeRating;
import dev.julco.kitchen.recipe.RecipeRatingRepository;
import dev.julco.kitchen.recipe.RecipeRepository;
import dev.julco.kitchen.shopping.ShoppingListItem;
import dev.julco.kitchen.shopping.ShoppingListItemRepository;
import dev.julco.kitchen.shopping.ShoppingListRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * A/B isolation (CLAUDE.md rule 1): two complete households, A and B. Every repository query made
 * with B's household id must see nothing of A, even when given A's ids.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HouseholdIsolationTest {

    @Autowired EntityManager em;
    @Autowired HouseholdMemberRepository members;
    @Autowired HouseholdInvitationRepository invitations;
    @Autowired PersonProfileRepository personProfiles;
    @Autowired HouseholdProfileRepository householdProfiles;
    @Autowired ApplianceRepository appliances;
    @Autowired RecipeRepository recipes;
    @Autowired RecipeRatingRepository ratings;
    @Autowired MenuRepository menus;
    @Autowired MenuItemRepository menuItems;
    @Autowired ShoppingListRepository shoppingLists;
    @Autowired ShoppingListItemRepository shoppingListItems;
    @Autowired PantryItemRepository pantryItems;
    @Autowired PdfExportRepository pdfExports;

    HouseholdFixture a;
    HouseholdFixture b;

    @BeforeEach
    void createTwoHouseholds() {
        a = HouseholdFixture.create(em, "Alice");
        b = HouseholdFixture.create(em, "Bob");
    }

    @Test
    void members_and_invitations() {
        assertThat(members.findSummariesByHouseholdId(b.householdId()))
                .extracting(MemberSummary::userId)
                .containsExactly(b.userId());
        assertThat(members.findByUserId(a.userId())).hasValueSatisfying(
                member -> assertThat(member.getHouseholdId()).isEqualTo(a.householdId()));
        assertThat(invitations.findAllByHouseholdId(b.householdId()))
                .extracting(HouseholdInvitation::getId)
                .containsExactly(b.invitationId());
    }

    @Test
    void profiles() {
        assertThat(personProfiles.findByUserId(b.userId())).hasValueSatisfying(
                profile -> assertThat(profile.getUserId()).isEqualTo(b.userId()));
        assertThat(householdProfiles.findByHouseholdId(b.householdId())).hasValueSatisfying(
                profile -> assertThat(profile.getHouseholdId()).isEqualTo(b.householdId()));
    }

    @Test
    void appliances() {
        assertThat(appliances.findByIdAndHouseholdId(a.applianceId(), b.householdId())).isEmpty();
        assertThat(appliances.findByIdAndHouseholdId(a.applianceId(), a.householdId())).isPresent();
        assertThat(appliances.findAllByHouseholdIdOrderByName(b.householdId()))
                .extracting(Appliance::getId)
                .containsExactly(b.applianceId());
    }

    @Test
    void recipes_and_ratings() {
        assertThat(recipes.findByIdAndHouseholdId(a.recipeId(), b.householdId())).isEmpty();
        assertThat(recipes.findByIdAndHouseholdId(a.recipeId(), a.householdId())).isPresent();
        assertThat(recipes.findAllByHouseholdIdOrderByTitle(b.householdId()))
                .extracting(Recipe::getId)
                .containsExactly(b.recipeId());
        assertThat(ratings.findAllByHouseholdIdAndRecipeIdOrderByCookedAtDesc(b.householdId(), a.recipeId()))
                .isEmpty();
        assertThat(ratings.findAllByHouseholdIdAndRecipeIdOrderByCookedAtDesc(a.householdId(), a.recipeId()))
                .extracting(RecipeRating::getId)
                .containsExactly(a.ratingId());
    }

    @Test
    void menus() {
        assertThat(menus.findByIdAndHouseholdId(a.menuId(), b.householdId())).isEmpty();
        assertThat(menus.findByIdAndHouseholdId(a.menuId(), a.householdId())).isPresent();
        assertThat(menus.findFirstByHouseholdIdOrderByStartsOnDesc(b.householdId())).hasValueSatisfying(
                menu -> assertThat(menu.getId()).isEqualTo(b.menuId()));
        assertThat(menuItems.findAllByHouseholdIdAndMenuIdOrderByServedOn(b.householdId(), a.menuId())).isEmpty();
        assertThat(menuItems.findAllByHouseholdIdAndMenuIdOrderByServedOn(a.householdId(), a.menuId()))
                .extracting(MenuItem::getId)
                .containsExactly(a.menuItemId());
    }

    @Test
    void shopping_lists() {
        assertThat(shoppingLists.findByIdAndHouseholdId(a.shoppingListId(), b.householdId())).isEmpty();
        assertThat(shoppingLists.findByIdAndHouseholdId(a.shoppingListId(), a.householdId())).isPresent();
        assertThat(shoppingLists.findFirstByHouseholdIdOrderByCreatedAtDesc(b.householdId())).hasValueSatisfying(
                list -> assertThat(list.getId()).isEqualTo(b.shoppingListId()));
        assertThat(shoppingListItems.findAllByHouseholdIdAndListIdOrderByPosition(b.householdId(), a.shoppingListId()))
                .isEmpty();
        assertThat(shoppingListItems.findAllByHouseholdIdAndListIdOrderByPosition(a.householdId(), a.shoppingListId()))
                .extracting(ShoppingListItem::getId)
                .containsExactly(a.shoppingListItemId());
    }

    @Test
    void pantry() {
        assertThat(pantryItems.findByIdAndHouseholdId(a.pantryItemId(), b.householdId())).isEmpty();
        assertThat(pantryItems.findByIdAndHouseholdId(a.pantryItemId(), a.householdId())).isPresent();
        assertThat(pantryItems.findAllByHouseholdIdOrderByName(b.householdId()))
                .extracting(PantryItem::getId)
                .containsExactly(b.pantryItemId());
    }

    @Test
    void pdf_exports() {
        assertThat(pdfExports.findByIdAndHouseholdId(a.pdfExportId(), b.householdId())).isEmpty();
        assertThat(pdfExports.findByIdAndHouseholdId(a.pdfExportId(), a.householdId())).isPresent();
    }
}
