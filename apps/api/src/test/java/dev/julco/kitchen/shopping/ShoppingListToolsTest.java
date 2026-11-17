package dev.julco.kitchen.shopping;

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

/** The shopping list tools as an AI client (or the widget, through its host) calls them, with A/B isolation. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ShoppingListToolsTest {

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
    void a_list_is_created_grouped_by_aisle_and_changed() throws Exception {
        DocumentContext created = mcp.call(a.userId(), "create_shopping_list", """
                {"title": "Week", "menuId": "%s",
                 "items": [{"name": "milk", "quantity": 2, "unit": "l", "aisle": "dairy"},
                           {"name": "leeks", "quantity": 3, "aisle": "produce"},
                           {"name": "salt"}]}""".formatted(a.menuId()));
        assertThat(created.read("$.isError", Boolean.class)).isNotEqualTo(Boolean.TRUE);
        String listId = created.read("$.structuredContent.list.id");
        assertThat(created.read("$.structuredContent.pageUrl", String.class)).endsWith("/list/" + listId);
        // Aisle order (produce before dairy), then the order the items were given.
        assertThat(created.read("$.structuredContent.list.items[*].name", List.class))
                .containsExactly("leeks", "milk", "salt");
        assertThat(created.read("$.structuredContent.list.remaining", Integer.class)).isEqualTo(3);

        String salt = created.read("$.structuredContent.list.items[2].id");
        String milk = created.read("$.structuredContent.list.items[1].id");
        DocumentContext updated = mcp.call(a.userId(), "update_shopping_list", """
                {"listId": "%s", "title": "Week, final", "remove": ["%s"],
                 "edit": [{"itemId": "%s", "quantity": 3}],
                 "add": [{"name": "eggs", "quantity": 6, "aisle": "dairy"}]}""".formatted(listId, salt, milk));
        assertThat(updated.read("$.structuredContent.list.title", String.class)).isEqualTo("Week, final");
        assertThat(updated.read("$.structuredContent.list.items[*].name", List.class))
                .containsExactly("leeks", "milk", "eggs");
        assertThat(updated.read("$.structuredContent.list.items[1].quantity", Double.class)).isEqualTo(3.0);

        DocumentContext latest = mcp.call(a.userId(), "get_shopping_list", "{}");
        assertThat(latest.read("$.structuredContent.list.id", String.class)).isEqualTo(listId);
        DocumentContext context = mcp.call(a.userId(), "get_context", "{}");
        assertThat(context.read("$.structuredContent.latestShoppingList.remaining", Integer.class)).isEqualTo(3);
    }

    @Test
    void checked_items_go_into_the_pantry_and_unchecking_undoes_it() throws Exception {
        DocumentContext created = mcp.call(a.userId(), "create_shopping_list", """
                {"title": "Top-up",
                 "items": [{"name": "Rice", "quantity": 1, "unit": "kg", "aisle": "dry_goods"},
                           {"name": "lemons", "quantity": 4, "aisle": "produce"}]}""");
        String listId = created.read("$.structuredContent.list.id");
        String lemons = created.read("$.structuredContent.list.items[0].id");
        String rice = created.read("$.structuredContent.list.items[1].id");

        DocumentContext checked = mcp.call(a.userId(), "check_items", """
                {"listId": "%s", "itemIds": ["%s", "%s"]}""".formatted(listId, lemons, rice));
        assertThat(checked.read("$.structuredContent.list.remaining", Integer.class)).isZero();
        assertThat(checked.read("$.structuredContent.list.items[0].checkedBy", String.class)).isEqualTo("Alice");
        // Checking again changes nothing (two members tapping the same item).
        mcp.call(a.userId(), "check_items", "{\"listId\": \"%s\", \"itemIds\": [\"%s\"]}".formatted(listId, rice));

        DocumentContext pantry = mcp.call(a.userId(), "get_pantry", "{}");
        assertThat(pantry.read("$.structuredContent.items[*].name", List.class)).containsExactly("lemons", "rice");
        assertThat(pantry.read("$.structuredContent.items[0].quantity", String.class)).isEqualTo("4");
        assertThat(pantry.read("$.structuredContent.items[0].source", String.class)).isEqualTo("shopping");
        // The fixture's "rice" (half a bag) was updated, not duplicated.
        assertThat(pantry.read("$.structuredContent.items[1].quantity", String.class)).isEqualTo("1 kg");

        mcp.call(a.userId(), "uncheck_items", """
                {"listId": "%s", "itemIds": ["%s", "%s"]}""".formatted(listId, lemons, rice));
        DocumentContext after = mcp.call(a.userId(), "get_pantry", "{}");
        assertThat(after.read("$.structuredContent.items[*].name", List.class)).containsExactly("rice");
        assertThat(after.read("$.structuredContent.items[0].quantity", String.class)).isEqualTo("half a bag");
    }

    @Test
    void removing_a_checked_item_takes_it_out_of_the_pantry() throws Exception {
        DocumentContext created = mcp.call(a.userId(), "create_shopping_list", """
                {"title": "Quick", "items": [{"name": "basil", "aisle": "produce"}]}""");
        String listId = created.read("$.structuredContent.list.id");
        String basil = created.read("$.structuredContent.list.items[0].id");
        mcp.call(a.userId(), "check_items", "{\"listId\": \"%s\", \"itemIds\": [\"%s\"]}".formatted(listId, basil));
        mcp.call(a.userId(), "update_shopping_list", "{\"listId\": \"%s\", \"remove\": [\"%s\"]}".formatted(listId, basil));

        DocumentContext pantry = mcp.call(a.userId(), "get_pantry", "{}");
        assertThat(pantry.read("$.structuredContent.items[*].name", List.class)).containsExactly("rice");
    }

    @Test
    void lists_of_another_household_are_invisible_and_untouchable() throws Exception {
        String aList = a.shoppingListId().toString();
        String aItem = a.shoppingListItemId().toString();
        List<String> calls = List.of(
                "get_shopping_list|{\"listId\": \"%s\"}".formatted(aList),
                "check_items|{\"listId\": \"%s\", \"itemIds\": [\"%s\"]}".formatted(aList, aItem),
                "uncheck_items|{\"listId\": \"%s\", \"itemIds\": [\"%s\"]}".formatted(aList, aItem),
                "update_shopping_list|{\"listId\": \"%s\", \"remove\": [\"%s\"]}".formatted(aList, aItem));
        for (String call : calls) {
            String[] parts = call.split("\\|", 2);
            DocumentContext result = mcp.call(b.userId(), parts[0], parts[1]);
            assertThat(result.read("$.isError", Boolean.class)).as(parts[0]).isTrue();
            assertThat(result.read("$.content[0].text", String.class)).as(parts[0]).contains("Shopping list not found");
        }

        // Bob's own list with Alice's item id: unknown item, nothing checked.
        DocumentContext foreignItem = mcp.call(b.userId(), "check_items", """
                {"listId": "%s", "itemIds": ["%s"]}""".formatted(b.shoppingListId(), aItem));
        assertThat(foreignItem.read("$.content[0].text", String.class)).contains("is not in this shopping list");

        DocumentContext foreignMenu = mcp.call(b.userId(), "create_shopping_list", """
                {"title": "Stolen", "menuId": "%s", "items": [{"name": "x"}]}""".formatted(a.menuId()));
        assertThat(foreignMenu.read("$.content[0].text", String.class)).contains("Menu not found");

        DocumentContext aView = mcp.call(a.userId(), "get_shopping_list", "{\"listId\": \"%s\"}".formatted(aList));
        assertThat(aView.read("$.structuredContent.list.items", List.class)).hasSize(1);
        assertThat(aView.read("$.structuredContent.list.remaining", Integer.class)).isEqualTo(1);
        assertThat(mcp.call(b.userId(), "get_shopping_list", "{}").jsonString()).doesNotContain("Alice");
    }
}
