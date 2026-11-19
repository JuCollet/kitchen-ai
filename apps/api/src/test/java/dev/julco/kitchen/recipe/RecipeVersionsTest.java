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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** A recipe's history: every version kept, rated, readable, and restorable, in the household's own book only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RecipeVersionsTest {

    private static final String VINAIGRETTE = """
            {%s"title": "Vinaigrette", "servings": 4, "tags": ["sauce"], "language": "fr",
             "ingredients": [{"name": "%s", "quantity": 6, "unit": "c. à s.", "aisle": "condiments"},
                             {"name": "vinaigre", "quantity": 2, "unit": "c. à s.", "aisle": "condiments"}],
             "steps": [{"text": "Mélanger."}]%s}""";

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

    private String save(String id, String oil, String changeNote) throws Exception {
        String head = id == null ? "" : "\"id\": \"%s\", ".formatted(id);
        String tail = changeNote == null ? "" : ", \"changeNote\": \"%s\"".formatted(changeNote);
        DocumentContext saved = call(a, "save_recipe", VINAIGRETTE.formatted(head, oil, tail));
        assertThat(saved.read("$.isError", Boolean.class)).isNotEqualTo(Boolean.TRUE);
        return saved.read("$.structuredContent.id");
    }

    @Test
    void a_change_that_was_not_good_is_undone_and_every_version_keeps_its_ratings() throws Exception {
        String id = save(null, "huile d'olive", null);
        call(a, "rate_recipe", "{\"recipeId\": \"%s\", \"score\": 8}".formatted(id));
        save(id, "huile de colza", "huile d'olive remplacée par huile de colza");
        call(a, "rate_recipe", "{\"recipeId\": \"%s\", \"score\": 6}".formatted(id));

        DocumentContext history = call(a, "list_recipe_versions", "{\"recipeId\": \"%s\"}".formatted(id));
        assertThat(history.read("$.structuredContent.currentVersion", Integer.class)).isEqualTo(2);
        assertThat(history.read("$.structuredContent.versions[*].version", List.class)).containsExactly(2, 1);
        assertThat(history.read("$.structuredContent.versions[0].changeNote", String.class))
                .isEqualTo("huile d'olive remplacée par huile de colza");
        assertThat(history.read("$.structuredContent.versions[0].householdScore", Double.class)).isEqualTo(6.0);
        assertThat(history.read("$.structuredContent.versions[1].householdScore", Double.class)).isEqualTo(8.0);
        assertThat(history.read("$.structuredContent.versions[1].savedBy", String.class)).isEqualTo("Alice");

        DocumentContext first = call(a, "get_recipe_version", "{\"recipeId\": \"%s\", \"version\": 1}".formatted(id));
        assertThat(first.read("$.structuredContent.recipe.ingredients[0].name", String.class)).isEqualTo("huile d'olive");

        DocumentContext restored = call(a, "restore_recipe_version", "{\"recipeId\": \"%s\", \"version\": 1}".formatted(id));
        // Going back creates version 3, equal to version 1: nothing is rewritten.
        assertThat(restored.read("$.structuredContent.version", Integer.class)).isEqualTo(3);
        assertThat(restored.read("$.structuredContent.ingredients[0].name", String.class)).isEqualTo("huile d'olive");
        DocumentContext after = call(a, "list_recipe_versions", "{\"recipeId\": \"%s\"}".formatted(id));
        assertThat(after.read("$.structuredContent.versions[*].version", List.class)).containsExactly(3, 2, 1);
        assertThat(after.read("$.structuredContent.versions[0].changeNote", String.class)).isEqualTo("Restored version 1");
        assertThat(after.read("$.structuredContent.versions[0].current", Boolean.class)).isTrue();
    }

    @Test
    void a_restored_version_still_follows_the_appliance_sheet() throws Exception {
        // Version 1 uses air_fry on the Speedi; the sheet then loses that mode.
        DocumentContext v1 = call(a, "save_recipe", """
                {"title": "Wings", "servings": 2, "applianceId": "%s", "tags": ["main"],
                 "ingredients": [{"name": "wings", "quantity": 500, "unit": "g", "aisle": "meat_fish"}],
                 "steps": [{"text": "Air fry.", "appliance_mode": "air_fry", "temperature": 200}]}"""
                .formatted(a.applianceId()));
        String id = v1.read("$.structuredContent.id");
        call(a, "save_appliance", """
                {"id": "%s", "name": "Speedi", "spec": {"modes": [{"name": "steam"}]}, "confirmedByUser": true}"""
                .formatted(a.applianceId()));
        call(a, "save_recipe", """
                {"id": "%s", "title": "Wings", "servings": 2, "tags": ["main"],
                 "ingredients": [{"name": "wings", "quantity": 500, "unit": "g", "aisle": "meat_fish"}],
                 "steps": [{"text": "Bake in the oven."}]}""".formatted(id));

        DocumentContext refused = call(a, "restore_recipe_version", "{\"recipeId\": \"%s\", \"version\": 1}".formatted(id));
        assertThat(refused.read("$.isError", Boolean.class)).isTrue();
        assertThat(refused.read("$.content[0].text", String.class)).contains("air_fry").contains("Its modes are: steam");
    }

    @Test
    void restoring_the_current_or_an_unknown_version_is_explained() throws Exception {
        String id = save(null, "huile d'olive", null);
        assertThat(call(a, "restore_recipe_version", "{\"recipeId\": \"%s\", \"version\": 1}".formatted(id))
                .read("$.content[0].text", String.class)).contains("already the current version");
        assertThat(call(a, "restore_recipe_version", "{\"recipeId\": \"%s\", \"version\": 7}".formatted(id))
                .read("$.content[0].text", String.class)).contains("use list_recipe_versions");
    }

    @Test
    void another_households_history_is_invisible_and_cannot_be_restored() throws Exception {
        String id = save(null, "huile d'olive", null);
        save(id, "huile de colza", "colza");
        List<String> calls = List.of(
                "list_recipe_versions|{\"recipeId\": \"%s\"}".formatted(id),
                "get_recipe_version|{\"recipeId\": \"%s\", \"version\": 1}".formatted(id),
                "restore_recipe_version|{\"recipeId\": \"%s\", \"version\": 1}".formatted(id));
        for (String c : calls) {
            String[] parts = c.split("\\|", 2);
            DocumentContext result = call(b, parts[0], parts[1]);
            assertThat(result.read("$.isError", Boolean.class)).as(parts[0]).isTrue();
            assertThat(result.read("$.content[0].text", String.class)).as(parts[0]).contains("Recipe not found");
        }
        assertThat(call(a, "list_recipe_versions", "{\"recipeId\": \"%s\"}".formatted(id))
                .read("$.structuredContent.currentVersion", Integer.class)).isEqualTo(2);
    }

    @Test
    void the_history_is_bounded_and_goes_with_the_recipe() throws Exception {
        String id = save(null, "huile 1", null);
        for (int i = 2; i <= RecipeService.MAX_VERSIONS + 2; i++) {
            save(id, "huile " + i, null);
        }
        DocumentContext history = call(a, "list_recipe_versions", "{\"recipeId\": \"%s\"}".formatted(id));
        List<Integer> kept = history.read("$.structuredContent.versions[*].version");
        assertThat(kept).hasSize(RecipeService.MAX_VERSIONS).startsWith(RecipeService.MAX_VERSIONS + 2).endsWith(3);

        call(a, "delete_recipe", "{\"recipeId\": \"%s\", \"confirmedByUser\": true}".formatted(id));
        assertThat(jdbc.queryForObject("select count(*) from recipe_version where recipe_id = ?::uuid", Integer.class, id))
                .isZero();
    }
}
