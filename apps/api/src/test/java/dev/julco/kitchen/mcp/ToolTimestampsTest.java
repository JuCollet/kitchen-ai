package dev.julco.kitchen.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.McpTestClient;
import dev.julco.kitchen.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dates in the result of the very call that creates or edits something: the AI reads them right
 * away, so they must never be null (reported from a real Claude: save_recipe returned
 * "updatedAt": null).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ToolTimestampsTest {

    private static final String RECIPE = """
            {"title": "Soupe", "servings": 2, "tags": ["soup"],
             "ingredients": [{"name": "eau", "quantity": 1, "unit": "l", "aisle": "drinks"}],
             "steps": [{"text": "Chauffer."}]}""";

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JWKSource<SecurityContext> jwkSource;

    McpTestClient mcp;
    HouseholdFixture a;

    @BeforeEach
    void createHousehold() {
        mcp = new McpTestClient(mvc, jwkSource);
        a = HouseholdFixture.create(em, "Alice");
    }

    @Test
    void a_new_or_edited_recipe_has_its_dates() throws Exception {
        DocumentContext created = mcp.call(a.userId(), "save_recipe", RECIPE);
        assertThat(created.read("$.structuredContent.updatedAt", String.class)).isNotNull();

        String id = created.read("$.structuredContent.id");
        String before = created.read("$.structuredContent.updatedAt");
        Thread.sleep(5);
        DocumentContext edited = mcp.call(a.userId(), "save_recipe",
                RECIPE.replaceFirst("\\{", "{\"id\": \"%s\", ".formatted(id)).replace("Soupe", "Soupe bis"));
        assertThat(edited.read("$.structuredContent.version", Integer.class)).isEqualTo(2);
        assertThat(edited.read("$.structuredContent.updatedAt", String.class)).isNotNull().isGreaterThan(before);
    }

    @Test
    void new_menus_lists_and_publications_have_their_dates() throws Exception {
        LocalDate today = LocalDate.now();
        DocumentContext menu = mcp.call(a.userId(), "create_menu", """
                {"title": "Week", "startsOn": "%s", "endsOn": "%s",
                 "items": [{"servedOn": "%s", "meal": "dinner", "recipeId": "%s", "servings": 2}]}"""
                .formatted(today, today, today, a.recipeId()));
        assertThat(menu.read("$.structuredContent.createdAt", String.class)).isNotNull();

        DocumentContext list = mcp.call(a.userId(), "create_shopping_list", """
                {"title": "List", "items": [{"name": "milk"}]}""");
        assertThat(list.read("$.structuredContent.list.createdAt", String.class)).isNotNull();

        DocumentContext published = mcp.call(a.userId(), "publish_recipe", "{\"recipeId\": \"%s\"}".formatted(a.recipeId()));
        assertThat(published.read("$.structuredContent.publishedAt", String.class)).isNotNull();
    }
}
