package dev.julco.kitchen.menu;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.McpTestClient;
import dev.julco.kitchen.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The menu tools and the menu part of get_context, as an AI client calls them, with A/B isolation. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MenuToolsTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JWKSource<SecurityContext> jwkSource;

    McpTestClient mcp;
    HouseholdFixture a;
    HouseholdFixture b;
    LocalDate today;

    @BeforeEach
    void createTwoHouseholds() {
        mcp = new McpTestClient(mvc, jwkSource);
        a = HouseholdFixture.create(em, "Alice");
        b = HouseholdFixture.create(em, "Bob");
        today = LocalDate.now(ZoneOffset.UTC);
    }

    @Test
    void a_menu_is_created_read_and_replaced() throws Exception {
        String soup = saveClassicRecipe(a, "Pumpkin soup");
        DocumentContext created = mcp.call(a.userId(), "create_menu", """
                {"title": "This week", "startsOn": "%s", "endsOn": "%s",
                 "requiredApplianceIds": ["%s"],
                 "items": [{"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 4},
                           {"servedOn": "%s", "meal": "lunch", "recipeId": "%s", "servings": 2}]}"""
                .formatted(today, today.plusDays(6), a.applianceId(),
                        today.plusDays(1), soup, today.plusDays(1), a.recipeId()));
        assertThat(created.read("$.isError", Boolean.class)).isNotEqualTo(Boolean.TRUE);
        String menuId = created.read("$.structuredContent.id");
        // Sorted by day, then breakfast to snack: lunch before dinner.
        assertThat(created.read("$.structuredContent.meals[*].meal", List.class)).containsExactly("lunch", "dinner");
        assertThat(created.read("$.structuredContent.meals[1].recipeTitle", String.class)).isEqualTo("Pumpkin soup");
        assertThat(created.read("$.structuredContent.periodAppliances.required[0].name", String.class)).isEqualTo("Speedi");

        DocumentContext latest = mcp.call(a.userId(), "get_menu", "{}");
        assertThat(latest.read("$.structuredContent.id", String.class)).isEqualTo(menuId);

        DocumentContext replaced = mcp.call(a.userId(), "create_menu", """
                {"menuId": "%s", "title": "This week, again", "startsOn": "%s", "endsOn": "%s",
                 "items": [{"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 3}]}"""
                .formatted(menuId, today, today.plusDays(6), today.plusDays(2), soup));
        assertThat(replaced.read("$.structuredContent.id", String.class)).isEqualTo(menuId);
        DocumentContext read = mcp.call(a.userId(), "get_menu", "{\"menuId\": \"%s\"}".formatted(menuId));
        assertThat(read.read("$.structuredContent.title", String.class)).isEqualTo("This week, again");
        assertThat(read.read("$.structuredContent.meals", List.class)).hasSize(1);
        assertThat(read.read("$.structuredContent.meals[0].servings", Integer.class)).isEqualTo(3);
        assertThat(read.read("$.structuredContent.periodAppliances.required", List.class)).isEmpty();
    }

    @Test
    void a_recipe_using_an_excluded_appliance_is_refused() throws Exception {
        DocumentContext result = mcp.call(a.userId(), "create_menu", """
                {"title": "No Speedi", "startsOn": "%s", "endsOn": "%s", "excludedApplianceIds": ["%s"],
                 "items": [{"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 4}]}"""
                .formatted(today, today.plusDays(6), a.applianceId(), today, a.recipeId()));

        assertThat(result.read("$.isError", Boolean.class)).isTrue();
        assertThat(result.read("$.content[0].text", String.class))
                .contains("items[0]").contains("Speedi").contains("excluded for this period");
    }

    @Test
    void periods_and_meals_are_checked() throws Exception {
        DocumentContext tooLong = mcp.call(a.userId(), "create_menu", """
                {"title": "Too long", "startsOn": "%s", "endsOn": "%s",
                 "items": [{"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 4}]}"""
                .formatted(today, today.plusDays(40), today, a.recipeId()));
        assertThat(tooLong.read("$.content[0].text", String.class)).contains("at most 31 days");

        DocumentContext outside = mcp.call(a.userId(), "create_menu", """
                {"title": "Outside", "startsOn": "%s", "endsOn": "%s",
                 "items": [{"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 4}]}"""
                .formatted(today, today.plusDays(2), today.plusDays(5), a.recipeId()));
        assertThat(outside.read("$.content[0].text", String.class)).contains("outside the menu's period");

        DocumentContext badMeal = mcp.call(a.userId(), "create_menu", """
                {"title": "Brunch", "startsOn": "%s", "endsOn": "%s",
                 "items": [{"servedOn": "%s", "meal": "brunch", "recipeId": "%s", "servings": 4}]}"""
                .formatted(today, today.plusDays(2), today, a.recipeId()));
        assertThat(badMeal.read("$.content[0].text", String.class)).contains("items[0].meal");
    }

    @Test
    void menus_of_another_household_are_invisible_and_untouchable() throws Exception {
        DocumentContext foreignRecipe = mcp.call(b.userId(), "create_menu", """
                {"title": "Stolen", "startsOn": "%s", "endsOn": "%s",
                 "items": [{"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 4}]}"""
                .formatted(today, today.plusDays(2), today, a.recipeId()));
        assertThat(foreignRecipe.read("$.isError", Boolean.class)).isTrue();
        assertThat(foreignRecipe.read("$.content[0].text", String.class)).contains("not found in this household's book");

        DocumentContext foreignAppliance = mcp.call(b.userId(), "create_menu", """
                {"title": "Mine", "startsOn": "%s", "endsOn": "%s", "excludedApplianceIds": ["%s"],
                 "items": [{"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 4}]}"""
                .formatted(today, today.plusDays(2), a.applianceId(), today, b.recipeId()));
        assertThat(foreignAppliance.read("$.content[0].text", String.class)).contains("not found in this household");

        DocumentContext read = mcp.call(b.userId(), "get_menu", "{\"menuId\": \"%s\"}".formatted(a.menuId()));
        assertThat(read.read("$.isError", Boolean.class)).isTrue();
        assertThat(read.read("$.content[0].text", String.class)).contains("Menu not found");

        DocumentContext replace = mcp.call(b.userId(), "create_menu", """
                {"menuId": "%s", "title": "Taken over", "startsOn": "%s", "endsOn": "%s",
                 "items": [{"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 4}]}"""
                .formatted(a.menuId(), today, today.plusDays(2), today, b.recipeId()));
        assertThat(replace.read("$.isError", Boolean.class)).isTrue();
        assertThat(replace.read("$.content[0].text", String.class)).contains("Menu not found");

        DocumentContext bLatest = mcp.call(b.userId(), "get_menu", "{}");
        assertThat(bLatest.read("$.structuredContent.title", String.class)).isEqualTo("Week Bob");
        assertThat(bLatest.jsonString()).doesNotContain("Alice");
        DocumentContext aMenu = mcp.call(a.userId(), "get_menu", "{\"menuId\": \"%s\"}".formatted(a.menuId()));
        assertThat(aMenu.read("$.structuredContent.title", String.class)).isEqualTo("Week Alice");
    }

    @Test
    void past_menu_meals_count_as_cooked_and_are_offered_for_rating() throws Exception {
        String soup = saveClassicRecipe(a, "Pumpkin soup");
        String curry = saveClassicRecipe(a, "Lentil curry");
        mcp.call(a.userId(), "create_menu", """
                {"title": "This week", "startsOn": "%s", "endsOn": "%s",
                 "items": [{"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 4},
                           {"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 4}]}"""
                .formatted(today.minusDays(1), today.plusDays(5), today.minusDays(1), soup, today.plusDays(1), curry));
        em.flush();

        // Served yesterday: cooked at noon UTC. Planned tomorrow: not cooked yet.
        DocumentContext search = mcp.call(a.userId(), "search_recipes", "{\"text\": \"pumpkin\"}");
        assertThat(search.read("$.structuredContent.results[0].lastCookedAt", String.class))
                .isEqualTo(today.minusDays(1) + "T12:00:00Z");
        DocumentContext notCooked = mcp.call(a.userId(), "search_recipes", "{\"notCookedForDays\": 3}");
        assertThat(notCooked.read("$.structuredContent.results[*].id", List.class)).contains(curry).doesNotContain(soup);
        DocumentContext detail = mcp.call(a.userId(), "get_recipe", "{\"recipeId\": \"%s\"}".formatted(soup));
        assertThat(detail.read("$.structuredContent.lastCookedAt", String.class)).startsWith(today.minusDays(1).toString());

        DocumentContext context = mcp.call(a.userId(), "get_context", "{}");
        assertThat(context.read("$.structuredContent.latestMenu.title", String.class)).isEqualTo("This week");
        assertThat(context.read("$.structuredContent.recipesToRate[*].recipeId", List.class)).containsExactly(soup);
        assertThat(context.read("$.structuredContent.pantry.items[0].name", String.class)).isEqualTo("rice");

        mcp.call(a.userId(), "rate_recipe", "{\"recipeId\": \"%s\", \"score\": 7}".formatted(soup));
        em.flush();
        DocumentContext after = mcp.call(a.userId(), "get_context", "{}");
        assertThat(after.read("$.structuredContent.recipesToRate", List.class)).isEmpty();

        // Ratings are personal: Bob's context never lists Alice's meals.
        DocumentContext bob = mcp.call(b.userId(), "get_context", "{}");
        assertThat(bob.read("$.structuredContent.recipesToRate", List.class)).isEmpty();
        assertThat(bob.read("$.structuredContent.latestMenu.title", String.class)).isEqualTo("Week Bob");
        assertThat(bob.jsonString()).doesNotContain("Pumpkin").doesNotContain("Alice");
    }

    private String saveClassicRecipe(HouseholdFixture household, String title) throws Exception {
        DocumentContext saved = mcp.call(household.userId(), "save_recipe", """
                {"title": "%s", "servings": 4, "language": "en",
                 "ingredients": [{"name": "water", "quantity": 1, "unit": "l", "aisle": "drinks"}],
                 "steps": [{"text": "Cook."}]}""".formatted(title));
        assertThat(saved.read("$.isError", Boolean.class)).isNotEqualTo(Boolean.TRUE);
        return saved.read("$.structuredContent.id");
    }

}
