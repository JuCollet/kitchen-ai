package dev.julco.kitchen.mcp;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** The MCP Apps contract: what a host needs to find, fetch and feed the Kitchen widget. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class McpAppsTest {

    private static final String WIDGET = "ui://kitchen/shopping-list.html";

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JWKSource<SecurityContext> jwkSource;

    HouseholdFixture alice;
    HouseholdFixture bob;

    @BeforeEach
    void createTwoHouseholds() {
        alice = HouseholdFixture.create(em, "Alice");
        bob = HouseholdFixture.create(em, "Bob");
    }

    @Test
    void the_tool_points_to_its_widget_in_both_metadata_forms() throws Exception {
        rpc(alice.userId(), "tools/list", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[?(@.name == 'spike_widget')]._meta.ui.resourceUri")
                        .value(hasItem(WIDGET)))
                .andExpect(jsonPath("$.result.tools[?(@.name == 'spike_widget')]._meta['ui/resourceUri']")
                        .value(hasItem(WIDGET)));
    }

    @Test
    void the_widget_is_listed_and_served_as_an_mcp_app() throws Exception {
        rpc(alice.userId(), "resources/list", "{}")
                .andExpect(jsonPath("$.result.resources[?(@.uri == '%s')].mimeType".formatted(WIDGET))
                        .value(hasItem(McpApps.MIME_TYPE)));

        rpc(alice.userId(), "resources/read", "{\"uri\": \"%s\"}".formatted(WIDGET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.contents[0].uri").value(WIDGET))
                .andExpect(jsonPath("$.result.contents[0].mimeType").value(McpApps.MIME_TYPE))
                .andExpect(jsonPath("$.result.contents[0].text").value(containsString("<html")))
                .andExpect(jsonPath("$.result.contents[0]._meta.ui.prefersBorder").value(true));
    }

    @Test
    void the_widget_data_only_shows_the_callers_household() throws Exception {
        rpc(bob.userId(), "tools/call", "{\"name\": \"spike_widget\", \"arguments\": {}}")
                .andExpect(jsonPath("$.result.structuredContent.household").value("Household Bob"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Alice"))));
    }

    @Test
    void the_widget_needs_a_token() throws Exception {
        mvc.perform(request(null, "resources/read", "{\"uri\": \"%s\"}".formatted(WIDGET)))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions rpc(UUID userId, String method, String params) throws Exception {
        return mvc.perform(request(token(userId), method, params));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String token, String method, String params) {
        var request = post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content("""
                        {"jsonrpc": "2.0", "id": 1, "method": "%s", "params": %s}""".formatted(method, params));
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String token(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost:5173")
                .subject(userId.toString())
                .audience(List.of("http://localhost:5173/mcp"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(JwsHeader.with(() -> "RS256").build(), claims))
                .getTokenValue();
    }
}
