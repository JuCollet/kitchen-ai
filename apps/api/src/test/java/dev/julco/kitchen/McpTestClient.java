package dev.julco.kitchen;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Calls the MCP endpoint as an AI client of a given person would: JSON-RPC over HTTP, with an
 * access token signed by Kitchen's key for the MCP resource. The OAuth flow that issues real
 * tokens is covered by McpOAuthFlowTest.
 */
public final class McpTestClient {

    public static final String ISSUER = "http://localhost:5173";

    private final MockMvc mvc;
    private final JWKSource<SecurityContext> jwkSource;

    public McpTestClient(MockMvc mvc, JWKSource<SecurityContext> jwkSource) {
        this.mvc = mvc;
        this.jwkSource = jwkSource;
    }

    public ResultActions rpc(UUID userId, String method, String paramsJson) throws Exception {
        var request = post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content("""
                        {"jsonrpc": "2.0", "id": 1, "method": "%s", "params": %s}""".formatted(method, paramsJson));
        if (userId != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId));
        }
        return mvc.perform(request);
    }

    /** Calls a tool and returns its JSON-RPC result ({@code content}, {@code structuredContent}, {@code isError}). */
    public DocumentContext call(UUID userId, String tool, String argumentsJson) throws Exception {
        String body = rpc(userId, "tools/call", """
                {"name": "%s", "arguments": %s}""".formatted(tool, argumentsJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.parse(JsonPath.parse(body).read("$.result", Object.class));
    }

    public String token(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .audience(List.of(ISSUER + "/mcp"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(JwsHeader.with(() -> "RS256").build(), claims))
                .getTokenValue();
    }
}
