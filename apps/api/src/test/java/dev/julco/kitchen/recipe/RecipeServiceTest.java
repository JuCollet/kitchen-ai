package dev.julco.kitchen.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.common.Aisle;
import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdMember;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.household.MemberRole;
import dev.julco.kitchen.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RecipeServiceTest {

    @Autowired EntityManager em;
    @Autowired RecipeService recipes;
    @Autowired HouseholdService households;

    HouseholdFixture a;
    HouseholdFixture b;
    CurrentMember alice;
    CurrentMember bob;

    @BeforeEach
    void createTwoHouseholds() {
        a = HouseholdFixture.create(em, "Alice");
        b = HouseholdFixture.create(em, "Bob");
        alice = households.resolveCurrentMember(a.userId()).orElseThrow();
        bob = households.resolveCurrentMember(b.userId()).orElseThrow();
    }

    @Test
    void a_recipe_is_saved_normalised_in_the_persons_language() {
        RecipeDetail saved = recipes.save(alice, classic("Soupe de tomates", List.of(" Soup ", "soup", "Quick")));

        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.language()).isEqualTo(Language.FR);
        assertThat(saved.tags()).containsExactly("soup", "quick");
        assertThat(saved.ingredients().getFirst().aisle()).isEqualTo(Aisle.PRODUCE);
        assertThat(saved.ingredients().get(1).aisle()).isEqualTo(Aisle.OTHER);
        assertThat(saved.appliance()).isNull();
    }

    @Test
    void editing_a_recipe_creates_a_new_version() {
        RecipeDetail saved = recipes.save(alice, classic("Soupe", null));

        RecipeDetail edited = recipes.save(alice, withId(saved.id(), classic("Soupe de tomates rôties", null)));

        assertThat(edited.id()).isEqualTo(saved.id());
        assertThat(edited.version()).isEqualTo(2);
        assertThat(edited.title()).isEqualTo("Soupe de tomates rôties");
    }

    @Test
    void a_recipe_only_uses_modes_of_its_appliance_sheet() {
        // The fixture's Speedi has a single mode: air_fry, 150 to 210 °C.
        RecipeDetail ok = recipes.save(alice, onSpeedi(a.applianceId(), "air_fry", 200));
        assertThat(ok.appliance().name()).isEqualTo("Speedi");

        assertThatThrownBy(() -> recipes.save(alice, onSpeedi(a.applianceId(), "steam", 100)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("steam")
                .hasMessageContaining("Its modes are: air_fry");
        assertThatThrownBy(() -> recipes.save(alice, onSpeedi(a.applianceId(), "air_fry", 230)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("150 to 210");
        assertThatThrownBy(() -> recipes.save(alice, onSpeedi(null, "air_fry", 200)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("not linked to an appliance");
    }

    @Test
    void another_household_appliance_cannot_be_used() {
        assertThatThrownBy(() -> recipes.save(bob, onSpeedi(a.applianceId(), "air_fry", 200)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Appliance not found");
    }

    @Test
    void another_household_recipe_cannot_be_read_edited_or_rated() {
        assertThatThrownBy(() -> recipes.get(bob, a.recipeId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> recipes.save(bob, withId(a.recipeId(), classic("Stolen", null))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> recipes.rate(bob, new RatingInput(a.recipeId(), 1, null, null, null)))
                .isInstanceOf(NotFoundException.class);
        assertThat(recipes.get(alice, a.recipeId()).title()).isEqualTo("Chicken Alice");
    }

    @Test
    void ratings_keep_their_history_and_the_household_averages_latest_scores() {
        CurrentMember carol = addMember(a, "Carol");
        RecipeDetail soup = recipes.save(alice, classic("Soupe", null));
        recipes.rate(alice, new RatingInput(soup.id(), 6, "Too salty", "Half the salt", LocalDate.now().minusDays(7)));
        recipes.save(alice, withId(soup.id(), classic("Soupe", null)));
        recipes.rate(alice, new RatingInput(soup.id(), 8, null, null, null));
        recipes.rate(carol, new RatingInput(soup.id(), 9, null, null, null));

        RecipeDetail detail = recipes.get(alice, soup.id());

        assertThat(detail.myScore()).isEqualTo(8);
        assertThat(detail.householdScore()).isEqualByComparingTo("8.5");
        assertThat(detail.ratings()).extracting(RatingView::score).containsExactlyInAnyOrder(9, 8, 6);
        assertThat(detail.ratings()).filteredOn(r -> r.score() == 6).singleElement().satisfies(rating -> {
            assertThat(rating.recipeVersion()).isEqualTo(1);
            assertThat(rating.adjustments()).isEqualTo("Half the salt");
            assertThat(rating.memberName()).isEqualTo("Alice");
            assertThat(rating.mine()).isTrue();
        });
        assertThat(detail.ratings()).filteredOn(r -> r.score() == 9).singleElement()
                .satisfies(rating -> assertThat(rating.recipeVersion()).isEqualTo(2));
    }

    @Test
    void inputs_are_bounded() {
        assertThatThrownBy(() -> recipes.save(alice, withServings(classic("Soupe", null), 51)))
                .isInstanceOf(ConstraintViolationException.class);
        RecipeInput tooManyTags = classic("Soupe", Collections.nCopies(21, "tag"));
        assertThatThrownBy(() -> recipes.save(alice, tooManyTags)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> recipes.rate(alice, new RatingInput(a.recipeId(), 11, null, null, null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> recipes.rate(alice, new RatingInput(a.recipeId(), 5, null, null, LocalDate.now().plusDays(2))))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private CurrentMember addMember(HouseholdFixture household, String name) {
        AppUser user = HouseholdFixture.createUser(em, name);
        em.persist(new HouseholdMember(household.householdId(), user.getId(), MemberRole.MEMBER));
        em.flush();
        return households.resolveCurrentMember(user.getId()).orElseThrow();
    }

    static RecipeInput classic(String title, List<String> tags) {
        return new RecipeInput(null, null, title, null, 4, 15, 40,
                List.of(new IngredientInput("tomates", new BigDecimal("800"), "g", "produce", null),
                        new IngredientInput("sel", null, null, null, null)),
                List.of(new RecipeStep("Cuire les tomates", 30, null, null)),
                tags, null, null, null, null);
    }

    static RecipeInput onSpeedi(UUID applianceId, String mode, int temperature) {
        return new RecipeInput(null, applianceId, "Poulet croustillant", null, 2, 10, 30,
                List.of(new IngredientInput("poulet", new BigDecimal("400"), "g", "meat_fish", null)),
                List.of(new RecipeStep("Préparer le poulet", 10, null, null),
                        new RecipeStep("Cuire", 20, mode, temperature)),
                null, null, null, null, null);
    }

    static RecipeInput withId(UUID id, RecipeInput input) {
        return new RecipeInput(id, input.applianceId(), input.title(), input.summary(), input.servings(),
                input.activeMinutes(), input.totalMinutes(), input.ingredients(), input.steps(), input.tags(),
                input.nutritionEstimate(), input.benefits(), input.language(), input.source());
    }

    static RecipeInput withServings(RecipeInput input, int servings) {
        return new RecipeInput(input.id(), input.applianceId(), input.title(), input.summary(), servings,
                input.activeMinutes(), input.totalMinutes(), input.ingredients(), input.steps(), input.tags(),
                input.nutritionEstimate(), input.benefits(), input.language(), input.source());
    }
}
