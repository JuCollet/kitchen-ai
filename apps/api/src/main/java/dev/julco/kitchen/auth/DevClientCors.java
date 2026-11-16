package dev.julco.kitchen.auth;

import java.util.List;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS for MCP clients that run in a browser on another origin, like the MCP Inspector in
 * development: discovery, registration, token and /mcp calls are cross-origin for them. Only the
 * origins of kitchen.mcp.allowed-origins are allowed (empty in production: no CORS at all). The
 * site itself is same-origin and never needs it; AI clients call from their servers.
 */
final class DevClientCors {

    private DevClientCors() {}

    static CorsConfigurationSource source(KitchenProperties kitchen) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = kitchen.mcp().allowedOrigins();
        if (origins.isEmpty()) {
            return source;
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setExposedHeaders(List.of("WWW-Authenticate", "Mcp-Session-Id", "MCP-Protocol-Version"));
        // Bearer tokens only: never send cookies cross-origin.
        cors.setAllowCredentials(false);
        for (String path : List.of("/.well-known/**", "/oauth2/register", "/oauth2/token", "/oauth2/jwks", "/mcp", "/mcp/**")) {
            source.registerCorsConfiguration(path, cors);
        }
        return source;
    }
}
