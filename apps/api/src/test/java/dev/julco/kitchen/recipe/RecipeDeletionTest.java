package dev.julco.kitchen.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** delete_recipe: a preview first, the deletion only once confirmed, and only in the household's own book. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RecipeDeletionTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
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

    private DocumentContext call(HouseholdFixture who, String tool, String arguments) throws Exception {
        DocumentContext result = mcp.call(who.userId(), tool, arguments);
        em.flush();
        return result;
    }

    private String delete(String recipeId, boolean confirmed) {
        return "{\"recipeId\": \"%s\", \"confirmedByUser\": %s}".formatted(recipeId, confirmed);
    }

    @Test
    void without_confirmation_nothing_is_deleted_and_the_consequences_are_listed() throws Exception {
        // The fixture recipe has one rating and one meal in a menu.
        DocumentContext preview = call(a, "delete_recipe", "{\"recipeId\": \"%s\"}".formatted(a.recipeId()));

        assertThat(preview.read("$.structuredContent.deleted", Boolean.class)).isFalse();
        assertThat(preview.read("$.structuredContent.title", String.class)).isEqualTo("Chicken Alice");
        assertThat(preview.read("$.structuredContent.ratings", Integer.class)).isEqualTo(1);
        assertThat(preview.read("$.structuredContent.menuMeals", Integer.class)).isEqualTo(1);
        assertThat(preview.read("$.structuredContent.message", String.class)).contains("ask them to confirm");
        assertThat(call(a, "get_recipe", "{\"recipeId\": \"%s\"}".formatted(a.recipeId()))
                .read("$.structuredContent.title", String.class)).isEqualTo("Chicken Alice");
    }

    @Test
    void once_confirmed_the_recipe_goes_with_its_ratings_menu_meals_and_publication() throws Exception {
        call(a, "publish_recipe", "{\"recipeId\": \"%s\"}".formatted(a.recipeId()));

        DocumentContext deleted = call(a, "delete_recipe", delete(a.recipeId().toString(), true));
        assertThat(deleted.read("$.structuredContent.deleted", Boolean.class)).isTrue();
        assertThat(deleted.read("$.structuredContent.published", Boolean.class)).isTrue();

        assertThat(call(a, "get_recipe", "{\"recipeId\": \"%s\"}".formatted(a.recipeId()))
                .read("$.content[0].text", String.class)).contains("Recipe not found");
        assertThat(jdbc.queryForObject("select count(*) from recipe_rating where recipe_id = ?", Integer.class,
                a.recipeId())).isZero();
        // The menu stays, without that meal.
        DocumentContext menu = call(a, "get_menu", "{\"menuId\": \"%s\"}".formatted(a.menuId()));
        assertThat(menu.read("$.structuredContent.meals", List.class)).isEmpty();
        assertThat(call(b, "search_shared_recipes", "{}").read("$.structuredContent.results", List.class)).isEmpty();
    }

    @Test
    void another_households_recipe_cannot_be_deleted_nor_previewed() throws Exception {
        for (boolean confirmed : List.of(false, true)) {
            DocumentContext result = call(b, "delete_recipe", delete(a.recipeId().toString(), confirmed));
            assertThat(result.read("$.isError", Boolean.class)).isTrue();
            assertThat(result.read("$.content[0].text", String.class)).contains("Recipe not found");
        }
        assertThat(call(a, "get_recipe", "{\"recipeId\": \"%s\"}".formatted(a.recipeId()))
                .read("$.structuredContent.ratings", List.class)).hasSize(1);
    }

    @Test
    void deleting_an_imported_copy_leaves_the_publication_alone() throws Exception {
        String sharedId = call(a, "publish_recipe", "{\"recipeId\": \"%s\"}".formatted(a.recipeId()))
                .read("$.structuredContent.sharedRecipeId");
        String copy = call(b, "import_shared_recipe", "{\"sharedRecipeId\": \"%s\"}".formatted(sharedId))
                .read("$.structuredContent.id");

        call(b, "delete_recipe", delete(copy, true));

        DocumentContext shared = call(b, "get_shared_recipe", "{\"sharedRecipeId\": \"%s\"}".formatted(sharedId));
        assertThat(shared.read("$.structuredContent.importedAs", Object.class)).isNull();
        assertThat(call(a, "get_recipe", "{\"recipeId\": \"%s\"}".formatted(a.recipeId()))
                .read("$.structuredContent.title", String.class)).isEqualTo("Chicken Alice");
    }

    @Test
    void the_tool_is_marked_destructive_for_clients() throws Exception {
        mcp.rpc(a.userId(), "tools/list", "{}")
                .andExpect(jsonPath("$.result.tools[?(@.name == 'delete_recipe')].annotations.destructiveHint")
                        .value(hasItem(true)));
    }
}
