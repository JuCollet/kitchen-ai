package dev.julco.kitchen.auth;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ConsentApiTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    @Test
    void the_consent_page_shows_the_client_name_and_where_tokens_go() throws Exception {
        AppUser user = HouseholdFixture.createUser(em, "Nina");
        String registration = mvc.perform(post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"client_name": "Claude", "token_endpoint_auth_method": "none",
                         "redirect_uris": ["https://claude.ai/api/mcp/auth_callback"]}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String clientId = JsonPath.read(registration, "$.client_id");

        mvc.perform(get("/api/consent").param("client_id", clientId)
                        .with(oidcLogin().idToken(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Claude"))
                .andExpect(jsonPath("$.redirectHosts[0]").value("claude.ai"));
    }

    @Test
    void unknown_clients_and_anonymous_requests_are_refused() throws Exception {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        mvc.perform(get("/api/consent").param("client_id", "unknown")
                        .with(oidcLogin().idToken(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/consent").param("client_id", "unknown")).andExpect(status().isUnauthorized());
    }
}
