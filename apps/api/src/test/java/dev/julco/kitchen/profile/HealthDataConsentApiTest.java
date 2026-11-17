package dev.julco.kitchen.profile;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.user.AppUser;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Health data consent is given on the site, with a session and CSRF protection, by the person only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HealthDataConsentApiTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    @Test
    void consent_is_given_and_withdrawn_by_the_signed_in_person() throws Exception {
        // Possible before joining a household: consent is asked when authorizing an AI.
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        mvc.perform(get("/api/me/health-data-consent").with(signedIn(user.getId())))
                .andExpect(jsonPath("$.consent").value(false));
        mvc.perform(consent(true).with(signedIn(user.getId())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consent").value(true));
        mvc.perform(consent(false).with(signedIn(user.getId())).with(csrf()))
                .andExpect(jsonPath("$.consent").value(false));
    }

    @Test
    void consent_needs_a_session_and_a_csrf_token() throws Exception {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        mvc.perform(consent(true).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(consent(true).with(signedIn(user.getId()))).andExpect(status().isForbidden());
    }

    @Test
    void an_access_token_cannot_give_consent() throws Exception {
        // The MCP security chain only serves /mcp: an AI client has no route to this endpoint.
        mvc.perform(consent(true).header("Authorization", "Bearer anything"))
                .andExpect(status().is4xxClientError());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder consent(boolean value) {
        return put("/api/me/health-data-consent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consent\": " + value + "}");
    }

    private static RequestPostProcessor signedIn(UUID userId) {
        return oidcLogin().idToken(token -> token.subject(userId.toString()));
    }
}
