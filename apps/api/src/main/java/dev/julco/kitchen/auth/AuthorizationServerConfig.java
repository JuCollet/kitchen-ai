package dev.julco.kitchen.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Kitchen is the OAuth authorization server of its own MCP server: when someone adds Kitchen to
 * their AI, the AI registers itself (RFC 7591), sends the person here to sign in and consent, and
 * receives a short-lived access token for /mcp only.
 *
 * <p>The person signs in with the browser session (Google or passkey): this chain only sends
 * unauthenticated people to the sign-in page, then the flow resumes where it stopped.
 */
@Configuration
class AuthorizationServerConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerSecurity(HttpSecurity http, KitchenProperties kitchen) throws Exception {
        http
                .cors(cors -> cors.configurationSource(DevClientCors.source(kitchen)))
                .oauth2AuthorizationServer(server -> {
                    http.securityMatcher(server.getEndpointsMatcher());
                    server
                            .authorizationEndpoint(authorization -> authorization.consentPage("/consent"))
                            .clientRegistrationEndpoint(registration -> registration
                                    // Open registration: see DynamicClientRegistration for why it is safe.
                                    .openRegistrationAllowed(true)
                                    .authenticationProviders(providers -> providers.forEach(provider -> {
                                        if (provider instanceof OAuth2ClientRegistrationAuthenticationProvider dcr) {
                                            dcr.setRegisteredClientConverter(DynamicClientRegistration.converter());
                                            dcr.setAuthenticationValidator(DynamicClientRegistration.validator());
                                        }
                                    })));
                })
                .authorizeHttpRequests(requests -> requests
                        // Open dynamic client registration (RFC 7591).
                        .requestMatchers(HttpMethod.POST, "/oauth2/register").permitAll()
                        .anyRequest().authenticated())
                // Not signed in during the authorization request: go to the site's sign-in page.
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/sign-in"), browserRequests()));
        return http.build();
    }

    /** A browser explicitly asking for HTML; an "Accept: *\/*" API client is not one. */
    private static MediaTypeRequestMatcher browserRequests() {
        MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        matcher.setIgnoredMediaTypes(java.util.Set.of(MediaType.ALL));
        return matcher;
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(KitchenProperties kitchen) {
        return AuthorizationServerSettings.builder().issuer(kitchen.issuer()).build();
    }

    /** AI clients, tokens and consents are stored in PostgreSQL (migration V4). */
    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcOperations jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcOperations jdbc, RegisteredClientRepository clients) {
        JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(jdbc, clients);
        JsonMapper json = authorizationJsonMapper();
        service.setAuthorizationRowMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(clients, json));
        service.setAuthorizationParametersMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(json));
        return service;
    }

    /**
     * During an AI's authorization request, the signed-in person (the Authentication) is stored as
     * JSON. Spring's default mapper knows Google sign-ins but not passkey sign-ins: add the WebAuthn
     * module, and allow its types in the polymorphic type validator (Jackson only deserializes types
     * it is explicitly allowed to, so stored JSON cannot instantiate arbitrary classes).
     */
    static JsonMapper authorizationJsonMapper() {
        WebauthnJacksonModule webauthn = new WebauthnJacksonModule();
        BasicPolymorphicTypeValidator.Builder allowedTypes = BasicPolymorphicTypeValidator.builder();
        webauthn.configurePolymorphicTypeValidator(allowedTypes);
        List<JacksonModule> modules = new ArrayList<>(
                SecurityJacksonModules.getModules(AuthorizationServerConfig.class.getClassLoader(), allowedTypes));
        modules.add(webauthn);
        return JsonMapper.builder().addModules(modules).build();
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcOperations jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(KitchenProperties kitchen) {
        return new ImmutableJWKSet<>(new JWKSet(SigningKeys.load(kitchen.oauth().signingKey())));
    }

    /** Required by Spring Security for the client registration endpoint. */
    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return NimbusJwtDecoder.withJwkSource(jwkSource).build();
    }

    /**
     * Access tokens are only valid for Kitchen's MCP server: their audience is its resource
     * identifier (RFC 8707), checked on every /mcp request. A token stolen from another service, or
     * issued for one, is useless here, and the other way around.
     *
     * <p>The claims are stored with the authorization as JSON with type information, and read back
     * when a refresh token is used. Spring Security only reads back allow-listed types: an
     * {@code ArrayList} is, the internal class behind {@code List.of} is not.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> accessTokenAudience(KitchenProperties kitchen) {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims().audience(new ArrayList<>(List.of(kitchen.mcpResource())));
            }
        };
    }
}
