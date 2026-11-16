package dev.julco.kitchen.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Security of the MCP endpoint: every request carries an OAuth access token issued by Kitchen
 * (Authorization header). No session, no cookie, so no CSRF.
 *
 * <p>Without a valid token: 401 with a WWW-Authenticate header pointing to the protected resource
 * metadata (RFC 9728), from which the AI client discovers Kitchen's authorization server.
 */
@Configuration
class McpSecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain mcpSecurity(
            HttpSecurity http, KitchenProperties kitchen, JWKSource<SecurityContext> jwkSource) throws Exception {
        http
                .securityMatcher("/mcp", "/mcp/**", "/.well-known/oauth-protected-resource/**")
                .cors(cors -> cors.configurationSource(DevClientCors.source(kitchen)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/.well-known/oauth-protected-resource/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(mcpTokenDecoder(jwkSource, kitchen)))
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(
                                builder -> builder
                                        .resource(kitchen.mcpResource())
                                        .authorizationServer(kitchen.issuer())
                                        .scope(DynamicClientRegistration.SCOPE)
                                        .resourceName("Kitchen")
                                        // Spring announces certificate-bound tokens by default; ours
                                        // are plain bearer tokens.
                                        .tlsClientCertificateBoundAccessTokens(false))))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new OriginValidationFilter(allowedOrigins(kitchen)), HeaderWriterFilter.class);
        return http.build();
    }

    /** Signature, expiry, issuer, and audience: the token must have been issued for this MCP server. */
    static NimbusJwtDecoder mcpTokenDecoder(JWKSource<SecurityContext> jwkSource, KitchenProperties kitchen) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains(kitchen.mcpResource()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(kitchen.issuer()), audience));
        return decoder;
    }

    private static Set<String> allowedOrigins(KitchenProperties kitchen) {
        Set<String> origins = new HashSet<>(kitchen.mcp().allowedOrigins());
        origins.add(kitchen.origin());
        return origins;
    }
}
