package dev.julco.kitchen.shopping;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** The site API of the fallback page: session, CSRF, and a 404 for another household's list. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ShoppingListApiTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    HouseholdFixture a;
    HouseholdFixture b;

    @BeforeEach
    void createTwoHouseholds() {
        a = HouseholdFixture.create(em, "Alice");
        b = HouseholdFixture.create(em, "Bob");
    }

    private static RequestPostProcessor signedIn(UUID userId) {
        return oidcLogin().idToken(token -> token.subject(userId.toString()));
    }

    private String items(UUID itemId) {
        return "{\"itemIds\": [\"%s\"]}".formatted(itemId);
    }

    @Test
    void a_member_reads_and_checks_their_list() throws Exception {
        mvc.perform(get("/api/shopping-lists/{id}", a.shoppingListId()).with(signedIn(a.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("List Alice"))
                .andExpect(jsonPath("$.items[0].aisle").value("meat_fish"));

        mvc.perform(post("/api/shopping-lists/{id}/check", a.shoppingListId()).with(signedIn(a.userId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(items(a.shoppingListItemId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].checked").value(true))
                .andExpect(jsonPath("$.remaining").value(0));

        mvc.perform(post("/api/shopping-lists/{id}/uncheck", a.shoppingListId()).with(signedIn(a.userId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(items(a.shoppingListItemId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].checked").value(false));
    }

    @Test
    void anonymous_and_csrf_less_requests_are_refused() throws Exception {
        mvc.perform(get("/api/shopping-lists/{id}", a.shoppingListId())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/shopping-lists/{id}/check", a.shoppingListId()).with(signedIn(a.userId()))
                        .contentType(MediaType.APPLICATION_JSON).content(items(a.shoppingListItemId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void another_households_list_is_a_404() throws Exception {
        mvc.perform(get("/api/shopping-lists/{id}", a.shoppingListId()).with(signedIn(b.userId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(content().string(not(containsString("Alice"))));
        mvc.perform(post("/api/shopping-lists/{id}/check", a.shoppingListId()).with(signedIn(b.userId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(items(a.shoppingListItemId())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/shopping-lists/{id}", UUID.randomUUID()).with(signedIn(b.userId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));

        mvc.perform(get("/api/shopping-lists/{id}", a.shoppingListId()).with(signedIn(a.userId())))
                .andExpect(jsonPath("$.items[0].checked").value(false));
    }

    @Test
    void request_bodies_are_validated() throws Exception {
        mvc.perform(post("/api/shopping-lists/{id}/check", a.shoppingListId()).with(signedIn(a.userId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemIds\": []}"))
                .andExpect(status().isBadRequest());
    }
}
