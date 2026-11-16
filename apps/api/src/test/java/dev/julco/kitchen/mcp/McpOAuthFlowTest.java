package dev.julco.kitchen.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * What an AI client (Claude, ChatGPT, MCP Inspector) does to connect to Kitchen, end to end:
 * discovery, dynamic registration, authorization with PKCE and consent, token, MCP call.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class McpOAuthFlowTest {

    private static final String ISSUER = "http://localhost:5173";
    private static final String RESOURCE = ISSUER + "/mcp";
    private static final String REDIRECT_URI = "https://claude.ai/api/mcp/auth_callback";

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JWKSource<SecurityContext> jwkSource;

    @Test
    void the_authorization_server_is_discoverable() throws Exception {
        mvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(ISSUER))
                .andExpect(jsonPath("$.registration_endpoint").value(ISSUER + "/oauth2/register"))
                .andExpect(jsonPath("$.code_challenge_methods_supported", hasItem("S256")));
    }

    @Test
    void the_mcp_server_publishes_its_protected_resource_metadata() throws Exception {
        // Both locations of the MCP spec: path-based, then root.
        for (String path : List.of("/.well-known/oauth-protected-resource/mcp", "/.well-known/oauth-protected-resource")) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resource").value(RESOURCE))
                    .andExpect(jsonPath("$.authorization_servers[0]").value(ISSUER))
                    .andExpect(jsonPath("$.scopes_supported", hasItem("kitchen")))
                    .andExpect(jsonPath("$.tls_client_certificate_bound_access_tokens").value(false));
        }
    }

    @Test
    void mcp_without_token_points_to_the_metadata() throws Exception {
        mcpCall(null, "ping")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("resource_metadata=\"http://localhost/.well-known/oauth-protected-resource\"")));
    }

    @Test
    void registration_keeps_only_safe_settings() throws Exception {
        mvc.perform(post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"client_name": "Claude", "redirect_uris": ["%s"],
                         "token_endpoint_auth_method": "none",
                         "grant_types": ["authorization_code", "refresh_token", "client_credentials"],
                         "scope": "admin"}""".formatted(REDIRECT_URI)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client_id").exists())
                .andExpect(jsonPath("$.grant_types", not(hasItem("client_credentials"))))
                .andExpect(jsonPath("$.scope").value("kitchen"));
    }

    @Test
    void tokens_are_only_sent_to_https_redirect_uris() throws Exception {
        mvc.perform(post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"client_name": "Evil", "redirect_uris": ["http://evil.example/callback"],
                         "token_endpoint_auth_method": "none"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_redirect_uri"));
    }

    @Test
    void a_person_who_is_not_signed_in_is_sent_to_the_sign_in_page() throws Exception {
        String clientId = registerClient();
        Pkce pkce = Pkce.create();

        mvc.perform(authorizationRequest(clientId, pkce).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "/sign-in"));
    }

    @Test
    void full_flow_from_registration_to_an_mcp_call() throws Exception {
        HouseholdFixture alice = HouseholdFixture.create(em, "Alice");

        String accessToken = connect(alice.userId());

        Map<String, Object> claims = claims(accessToken);
        assertThat(claims.get("sub")).isEqualTo(alice.userId().toString());
        // RFC 7519: a single audience may be serialized as a string or as an array.
        assertThat(claims.get("aud")).isIn(RESOURCE, List.of(RESOURCE));
        mcpCall(accessToken, "ping")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("Household Alice")));
    }

    @Test
    void each_token_only_sees_its_own_household() throws Exception {
        HouseholdFixture alice = HouseholdFixture.create(em, "Alice");
        HouseholdFixture bob = HouseholdFixture.create(em, "Bob");

        mcpCall(connect(bob.userId()), "ping")
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("Household Bob")))
                .andExpect(jsonPath("$.result.content[0].text").value(not(containsString("Alice"))));
        mcpCall(connect(alice.userId()), "ping")
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("Household Alice")));
    }

    @Test
    void a_token_issued_for_another_resource_is_refused() throws Exception {
        HouseholdFixture alice = HouseholdFixture.create(em, "Alice");
        String foreignToken = signedToken(alice.userId(), "https://other.example/api");

        mcpCall(foreignToken, "ping").andExpect(status().isUnauthorized());
        mcpCall(signedToken(alice.userId(), RESOURCE), "ping").andExpect(status().isOk());
    }

    @Test
    void a_foreign_browser_origin_is_refused() throws Exception {
        HouseholdFixture alice = HouseholdFixture.create(em, "Alice");
        String token = signedToken(alice.userId(), RESOURCE);

        mvc.perform(mcpRequest(token, "ping").header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(mcpRequest(token, "ping").header(HttpHeaders.ORIGIN, "http://localhost:6274"))
                .andExpect(status().isOk());
    }

    @Test
    void a_browser_based_dev_client_gets_cors_headers_and_others_do_not() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/oauth2/token")
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:6274")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:6274"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/oauth2/token")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    // --- What an AI client does --------------------------------------------------------------

    /** Registers a client, gets the person's consent, exchanges the code: returns an access token. */
    private String connect(UUID userId) throws Exception {
        String clientId = registerClient();
        Pkce pkce = Pkce.create();
        RequestPostProcessor person = oidcLogin().idToken(token -> token.subject(userId.toString()));

        // 1. Authorization request: the signed-in person is sent to the consent page.
        String consentUrl = mvc.perform(authorizationRequest(clientId, pkce).with(person))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(consentUrl).contains("/consent?");
        Map<String, String> consent = queryParameters(consentUrl);

        // 2. The person consents: back to the client with an authorization code.
        String callback = mvc.perform(post("/oauth2/authorize").with(person)
                        .param("client_id", clientId)
                        .param("state", consent.get("state"))
                        .param("scope", "kitchen"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(callback).startsWith(REDIRECT_URI);
        Map<String, String> response = queryParameters(callback);
        assertThat(response.get("state")).isEqualTo("client-state");

        // 3. The client exchanges the code, proving it holds the PKCE verifier.
        String tokenResponse = mvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("code", response.get("code"))
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", clientId)
                        .param("code_verifier", pkce.verifier()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(tokenResponse, "$.access_token");
    }

    /** Decoded query parameters of a redirect URL. */
    private static Map<String, String> queryParameters(String url) {
        Map<String, String> parameters = new java.util.HashMap<>();
        UriComponentsBuilder.fromUriString(url).build().getQueryParams().toSingleValueMap()
                .forEach((name, value) -> parameters.put(name, java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)));
        return parameters;
    }

    private String registerClient() throws Exception {
        String registration = mvc.perform(post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"client_name": "Claude", "redirect_uris": ["%s"], "token_endpoint_auth_method": "none"}"""
                        .formatted(REDIRECT_URI)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(registration, "$.client_id");
    }

    /** Spring Security reads GET authorization parameters from the query string, as a browser sends them. */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorizationRequest(
            String clientId, Pkce pkce) {
        String uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("scope", "kitchen")
                .queryParam("state", "client-state")
                .queryParam("resource", RESOURCE)
                .queryParam("code_challenge", pkce.challenge())
                .queryParam("code_challenge_method", "S256")
                .encode()
                .toUriString();
        return get(uri);
    }

    private ResultActions mcpCall(String token, String tool) throws Exception {
        return mvc.perform(mcpRequest(token, tool));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder mcpRequest(
            String token, String tool) {
        var request = post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content("""
                        {"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "%s", "arguments": {}}}"""
                        .formatted(tool));
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** A token signed with Kitchen's key, as if Kitchen had issued it for {@code audience}. */
    private String signedToken(UUID userId, String audience) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(JwsHeader.with(() -> "RS256").build(), claims))
                .getTokenValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> claims(String jwt) {
        String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonPath.parse(payload).read("$", Map.class);
    }

    private record Pkce(String verifier, String challenge) {

        static Pkce create() throws Exception {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return new Pkce(verifier, Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        }
    }
}
