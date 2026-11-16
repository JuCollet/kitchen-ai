package dev.julco.kitchen.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.user.AppUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Site API: authentication, CSRF, validation, invitations and A/B isolation between households. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HouseholdApiTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    /** A signed-in session whose principal name is the Kitchen user id, as after a Google sign-in. */
    private static RequestPostProcessor signedIn(UUID userId) {
        return oidcLogin().idToken(token -> token.subject(userId.toString()));
    }

    @Test
    void every_api_endpoint_requires_a_session() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/household/members")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/households").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Home\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void state_changing_requests_require_the_csrf_token() throws Exception {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        mvc.perform(post("/api/households").with(signedIn(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Home\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void me_without_household() throws Exception {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        mvc.perform(get("/api/me").with(signedIn(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.locale").value("nl"))
                .andExpect(jsonPath("$.household").doesNotExist());
        mvc.perform(get("/api/household/members").with(signedIn(user.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_a_household_then_see_it() throws Exception {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        mvc.perform(post("/api/households").with(signedIn(user.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Home\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Home"))
                .andExpect(jsonPath("$.role").value("owner"));
        mvc.perform(get("/api/me").with(signedIn(user.getId())))
                .andExpect(jsonPath("$.household.name").value("Home"));
        mvc.perform(post("/api/households").with(signedIn(user.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Second\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_in_household"));
    }

    @Test
    void household_names_are_validated() throws Exception {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        mvc.perform(post("/api/households").with(signedIn(user.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/households").with(signedIn(user.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void members_only_lists_the_current_household() throws Exception {
        HouseholdFixture a = HouseholdFixture.create(em, "Alice");
        HouseholdFixture b = HouseholdFixture.create(em, "Bob");

        mvc.perform(get("/api/household/members").with(signedIn(b.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(b.userId().toString()));
        mvc.perform(get("/api/household/members").with(signedIn(a.userId())))
                .andExpect(jsonPath("$[0].userId").value(a.userId().toString()));
    }

    @Test
    void an_invitation_lets_someone_join_the_household_once() throws Exception {
        HouseholdFixture alice = HouseholdFixture.create(em, "Alice");
        AppUser nina = HouseholdFixture.createUser(em, "Nina");
        AppUser omar = HouseholdFixture.createUser(em, "Omar");

        String token = createInvitation(alice.userId());
        em.flush();
        // Only the hash is stored, never the token.
        assertThat(jdbc.queryForObject(
                        "select count(*) from household_invitation where token_hash = ?",
                        Integer.class, InvitationService.hash(token)))
                .isOne();

        accept(nina.getId(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alice.householdId().toString()))
                .andExpect(jsonPath("$.role").value("member"));
        mvc.perform(get("/api/household/members").with(signedIn(alice.userId())))
                .andExpect(jsonPath("$.length()").value(2));
        // Single use.
        accept(omar.getId(), token).andExpect(status().isNotFound());
    }

    @Test
    void unknown_and_expired_invitations_are_not_found() throws Exception {
        HouseholdFixture alice = HouseholdFixture.create(em, "Alice");
        AppUser nina = HouseholdFixture.createUser(em, "Nina");
        String expired = "expired-token";
        em.persist(new HouseholdInvitation(
                alice.householdId(), InvitationService.hash(expired), alice.userId(),
                Instant.now().minus(1, ChronoUnit.MINUTES)));
        em.flush();

        accept(nina.getId(), "unknown-token").andExpect(status().isNotFound());
        accept(nina.getId(), expired).andExpect(status().isNotFound());
    }

    @Test
    void a_member_of_another_household_cannot_accept_an_invitation() throws Exception {
        HouseholdFixture alice = HouseholdFixture.create(em, "Alice");
        HouseholdFixture bob = HouseholdFixture.create(em, "Bob");

        accept(bob.userId(), createInvitation(alice.userId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_in_household"));
    }

    private String createInvitation(UUID userId) throws Exception {
        String body = mvc.perform(post("/api/household/invitations").with(signedIn(userId)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private org.springframework.test.web.servlet.ResultActions accept(UUID userId, String token) throws Exception {
        return mvc.perform(post("/api/invitations/accept").with(signedIn(userId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
    }
}
