package dev.julco.kitchen.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.McpTestClient;
import dev.julco.kitchen.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The recipe tools as an AI client calls them, with A/B isolation between households. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RecipeToolsTest {

    private static final String IMPORTED = """
            {"title": "Shakshuka", "servings": 2, "totalMinutes": 30, "source": "https://example.test/shakshuka",
             "language": "en", "tags": ["Vegetarian"],
             "ingredients": [{"name": "eggs", "quantity": 4, "aisle": "dairy"},
                             {"name": "canned tomatoes", "quantity": 400, "unit": "g", "aisle": "canned"},
                             {"name": "cumin", "note": "ground"}],
             "steps": [{"text": "Simmer the tomatoes with the cumin.", "duration_min": 15},
                       {"text": "Add the eggs, cover and cook.", "duration_min": 8}],
             "nutritionEstimate": {"calories_kcal": 320, "protein_g": 18}}""";

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JWKSource<SecurityContext> jwkSource;

    McpTestClient mcp;
    HouseholdFixture a;
    HouseholdFixture b;

    @BeforeEach
    void createTwoHouseholds() {
        mcp = new McpTestClient(mvc, jwkSource);
        a = HouseholdFixture.create(em, "Alice");
        b = HouseholdFixture.create(em, "Bob");
    }

    @Test
    void an_imported_recipe_is_saved_found_and_rated() throws Exception {
        DocumentContext saved = mcp.call(a.userId(), "save_recipe", IMPORTED);
        assertThat(saved.read("$.isError", Boolean.class)).isNotEqualTo(Boolean.TRUE);
        String id = saved.read("$.structuredContent.id");
        assertThat(saved.read("$.structuredContent.ingredients[1].aisle", String.class)).isEqualTo("canned");
        assertThat(saved.read("$.structuredContent.ingredients[2].aisle", String.class)).isEqualTo("other");
        assertThat(saved.read("$.structuredContent.tags", List.class)).containsExactly("vegetarian");

        // The test transaction spans every call: the search (SQL) must see the unflushed recipe.
        em.flush();
        DocumentContext found = mcp.call(a.userId(), "search_recipes", """
                {"text": "shakshuka"}""");
        assertThat(found.read("$.structuredContent.results[*].id", List.class)).containsExactly(id);

        DocumentContext rated = mcp.call(a.userId(), "rate_recipe", """
                {"recipeId": "%s", "score": 8, "adjustments": "More cumin", "cookedOn": "2026-11-15"}""".formatted(id));
        assertThat(rated.read("$.structuredContent.score", Integer.class)).isEqualTo(8);

        DocumentContext detail = mcp.call(a.userId(), "get_recipe", """
                {"recipeId": "%s"}""".formatted(id));
        assertThat(detail.read("$.structuredContent.myScore", Integer.class)).isEqualTo(8);
        assertThat(detail.read("$.structuredContent.ratings[0].adjustments", String.class)).isEqualTo("More cumin");
        assertThat(detail.read("$.structuredContent.steps[0].duration_min", Integer.class)).isEqualTo(15);
    }

    @Test
    void an_invented_mode_is_refused_with_the_real_ones() throws Exception {
        DocumentContext result = mcp.call(a.userId(), "save_recipe", """
                {"title": "Wings", "servings": 2, "applianceId": "%s",
                 "ingredients": [{"name": "chicken wings", "quantity": 500, "unit": "g", "aisle": "meat_fish"}],
                 "steps": [{"text": "Bake the wings.", "appliance_mode": "bake", "temperature": 200}]}"""
                .formatted(a.applianceId()));

        assertThat(result.read("$.isError", Boolean.class)).isTrue();
        assertThat(result.read("$.content[0].text", String.class)).contains("bake").contains("Its modes are: air_fry");
    }

    @Test
    void recipes_of_another_household_are_invisible() throws Exception {
        DocumentContext search = mcp.call(b.userId(), "search_recipes", "{}");
        assertThat(search.jsonString()).doesNotContain(a.recipeId().toString()).doesNotContain("Chicken Alice");

        for (String tool : List.of("get_recipe", "rate_recipe")) {
            String args = tool.equals("get_recipe")
                    ? "{\"recipeId\": \"%s\"}".formatted(a.recipeId())
                    : "{\"recipeId\": \"%s\", \"score\": 1}".formatted(a.recipeId());
            DocumentContext result = mcp.call(b.userId(), tool, args);
            assertThat(result.read("$.isError", Boolean.class)).as(tool).isTrue();
            assertThat(result.read("$.content[0].text", String.class)).as(tool).contains("use search_recipes");
        }

        DocumentContext edit = mcp.call(b.userId(), "save_recipe", IMPORTED.replaceFirst("\\{", "{\"id\": \"%s\", ".formatted(a.recipeId())));
        assertThat(edit.read("$.isError", Boolean.class)).isTrue();
        DocumentContext mine = mcp.call(a.userId(), "get_recipe", "{\"recipeId\": \"%s\"}".formatted(a.recipeId()));
        assertThat(mine.read("$.structuredContent.title", String.class)).isEqualTo("Chicken Alice");
        assertThat(mine.read("$.structuredContent.ratings", List.class)).hasSize(1);
    }
}
