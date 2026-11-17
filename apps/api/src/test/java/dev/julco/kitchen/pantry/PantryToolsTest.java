package dev.julco.kitchen.pantry;

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

/** The pantry tools as an AI client calls them, with A/B isolation between households. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PantryToolsTest {

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
    void items_are_added_updated_by_name_and_removed() throws Exception {
        DocumentContext added = mcp.call(a.userId(), "update_pantry", """
                {"upsert": [{"name": "Eggs", "quantity": "6", "aisle": "dairy"},
                            {"name": "RICE", "quantity": "a full bag"}]}""");
        assertThat(added.read("$.isError", Boolean.class)).isNotEqualTo(Boolean.TRUE);
        // "RICE" updated the fixture's "rice": same name, case ignored, aisle kept.
        assertThat(added.read("$.structuredContent.items[*].name", List.class)).containsExactly("Eggs", "rice");
        assertThat(added.read("$.structuredContent.items[1].quantity", String.class)).isEqualTo("a full bag");
        assertThat(added.read("$.structuredContent.items[1].aisle", String.class)).isEqualTo("dry_goods");
        assertThat(added.read("$.structuredContent.items[0].source", String.class)).isEqualTo("manual");

        DocumentContext removed = mcp.call(a.userId(), "update_pantry", """
                {"remove": ["eggs", "saffron"]}""");
        assertThat(removed.read("$.structuredContent.items[*].name", List.class)).containsExactly("rice");
        assertThat(removed.read("$.structuredContent.notFound", List.class)).containsExactly("saffron");

        DocumentContext pantry = mcp.call(a.userId(), "get_pantry", "{}");
        assertThat(pantry.read("$.structuredContent.items[*].name", List.class)).containsExactly("rice");
    }

    @Test
    void invalid_updates_are_refused() throws Exception {
        DocumentContext duplicate = mcp.call(a.userId(), "update_pantry", """
                {"upsert": [{"name": "milk"}], "remove": ["Milk"]}""");
        assertThat(duplicate.read("$.isError", Boolean.class)).isTrue();
        assertThat(duplicate.read("$.content[0].text", String.class)).contains("more than once");

        DocumentContext badAisle = mcp.call(a.userId(), "update_pantry", """
                {"upsert": [{"name": "milk", "aisle": "fridge"}]}""");
        assertThat(badAisle.read("$.content[0].text", String.class)).contains("upsert[0].aisle");
    }

    @Test
    void the_pantry_of_another_household_is_invisible_and_untouchable() throws Exception {
        DocumentContext bPantry = mcp.call(b.userId(), "get_pantry", "{}");
        assertThat(bPantry.read("$.structuredContent.items", List.class)).hasSize(1);
        assertThat(bPantry.read("$.structuredContent.items[0].id", String.class)).isEqualTo(b.pantryItemId().toString());

        // Bob updating and removing "rice" only touches Bob's own rice.
        mcp.call(b.userId(), "update_pantry", "{\"upsert\": [{\"name\": \"rice\", \"quantity\": \"none\"}]}");
        mcp.call(b.userId(), "update_pantry", "{\"remove\": [\"rice\"]}");

        DocumentContext aPantry = mcp.call(a.userId(), "get_pantry", "{}");
        assertThat(aPantry.read("$.structuredContent.items[0].id", String.class)).isEqualTo(a.pantryItemId().toString());
        assertThat(aPantry.read("$.structuredContent.items[0].quantity", String.class)).isEqualTo("half a bag");
        assertThat(mcp.call(b.userId(), "get_pantry", "{}").read("$.structuredContent.items", List.class)).isEmpty();
    }
}
