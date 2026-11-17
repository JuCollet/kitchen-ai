package dev.julco.kitchen.recipe;

import static dev.julco.kitchen.recipe.RecipeServiceTest.classic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDate;
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
class RecipeSearchTest {

    @Autowired EntityManager em;
    @Autowired RecipeService recipes;
    @Autowired HouseholdService households;

    HouseholdFixture a;
    HouseholdFixture b;
    CurrentMember alice;
    CurrentMember bob;

    @BeforeEach
    void createTwoBooks() {
        a = HouseholdFixture.create(em, "Alice");
        b = HouseholdFixture.create(em, "Bob");
        alice = households.resolveCurrentMember(a.userId()).orElseThrow();
        bob = households.resolveCurrentMember(b.userId()).orElseThrow();
        // Alice's book: "Chicken Alice" (fixture, Speedi, rated 8 today), and two classic recipes.
        recipes.save(alice, classic("Crème brûlée", List.of("dessert")));
        recipes.save(alice, classic("Soupe de tomates", List.of("soup", "quick")));
        em.flush();
    }

    @Test
    void text_search_ignores_accents_and_plurals() {
        assertThat(titles(alice, criteria("creme brulee"))).containsExactly("Crème brûlée");
        // French stemming: the plural "soupes" finds "Soupe de tomates".
        assertThat(titles(alice, criteria("soupes"))).containsExactly("Soupe de tomates");
        assertThat(titles(alice, criteria("poulet OR chicken"))).containsExactly("Chicken Alice");
    }

    @Test
    void filters_combine() {
        assertThat(titles(alice, new RecipeSearch(null, List.of("Quick", "soup"), null, null, null, null, null, null, null)))
                .containsExactly("Soupe de tomates");
        assertThat(titles(alice, new RecipeSearch(null, null, a.applianceId(), null, null, null, null, null, null)))
                .containsExactly("Chicken Alice");
        assertThat(titles(alice, new RecipeSearch(null, null, null, true, null, null, null, null, null)))
                .containsExactly("Crème brûlée", "Soupe de tomates");
        assertThat(titles(alice, new RecipeSearch(null, null, null, null, 8, null, null, null, null)))
                .containsExactly("Chicken Alice");
        assertThat(titles(alice, new RecipeSearch(null, null, null, null, null, 9, null, null, null))).isEmpty();
        // Rated (cooked) today: not "not cooked for 7 days"; never-cooked recipes are.
        assertThat(titles(alice, new RecipeSearch(null, null, null, null, null, null, 7, null, null)))
                .containsExactly("Crème brûlée", "Soupe de tomates");
    }

    @Test
    void last_cooked_date_comes_from_the_latest_rating() {
        UUID soup = idOf("Soupe de tomates");
        recipes.rate(alice, new RatingInput(soup, 7, null, null, LocalDate.now().minusDays(30)));
        em.flush();

        RecipeSummary summary = recipes.search(alice, criteria("soupe")).results().getFirst();
        assertThat(summary.myScore()).isEqualTo(7);
        assertThat(summary.raterCount()).isOne();
        assertThat(titles(alice, new RecipeSearch(null, null, null, null, null, null, 20, null, null)))
                .contains("Soupe de tomates");
    }

    @Test
    void results_are_paginated() {
        RecipeSearchPage first = recipes.search(alice, new RecipeSearch(null, null, null, null, null, null, null, 0, 2));
        RecipeSearchPage second = recipes.search(alice, new RecipeSearch(null, null, null, null, null, null, null, 1, 2));

        assertThat(first.results()).hasSize(2);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.results()).extracting(RecipeSummary::title).containsExactly("Soupe de tomates");
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void no_criterion_ever_reaches_another_household() {
        List<RecipeSearch> searches = List.of(
                new RecipeSearch(null, null, null, null, null, null, null, null, null),
                criteria("chicken"),
                criteria("creme"),
                new RecipeSearch(null, null, a.applianceId(), null, null, null, null, null, null),
                new RecipeSearch(null, List.of("dessert"), null, null, null, null, null, null, null),
                new RecipeSearch(null, null, null, null, 1, null, null, null, null),
                new RecipeSearch(null, null, null, null, null, 1, null, null, null));
        for (RecipeSearch search : searches) {
            assertThat(recipes.search(bob, search).results())
                    .as(search.toString())
                    .extracting(RecipeSummary::title)
                    .allMatch(title -> title.equals("Chicken Bob"));
        }
    }

    @Test
    void criteria_are_validated() {
        assertThatThrownBy(() -> recipes.search(alice, new RecipeSearch(null, null, a.applianceId(), true, null, null, null, null, null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> recipes.search(alice, new RecipeSearch(null, null, null, null, null, null, null, null, 21)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> recipes.search(alice, criteria("x".repeat(201))))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private UUID idOf(String title) {
        return recipes.search(alice, criteria(title)).results().getFirst().id();
    }

    private List<String> titles(CurrentMember member, RecipeSearch search) {
        return recipes.search(member, search).results().stream().map(RecipeSummary::title).toList();
    }

    private static RecipeSearch criteria(String text) {
        return new RecipeSearch(text, null, null, null, null, null, null, null, null);
    }
}
