package dev.julco.kitchen.auth;

import static org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/**
 * Browser security: Google sign-in, passkeys, server-side session cookie, CSRF protection.
 *
 * <p>Three filter chains, tried in order: the OAuth authorization server for AI clients
 * ({@link AuthorizationServerConfig}), the MCP endpoint ({@link McpSecurityConfig}), then this one
 * for everything else.
 */
@Configuration
@EnableConfigurationProperties(KitchenProperties.class)
class SecurityConfig {

    @Bean
    @Order(3)
    SecurityFilterChain browserSecurity(
            HttpSecurity http, GoogleOidcUserService googleUsers, KitchenProperties kitchen) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error", "/api/csrf").permitAll()
                        // Passkey sign-in: the person is not authenticated yet.
                        .requestMatchers(HttpMethod.POST, "/webauthn/authenticate/options", "/login/webauthn")
                        .permitAll()
                        // Everything else, including passkey registration, needs a signed-in person.
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        // Sign-in page of the React site; Spring's generated page is never used.
                        .loginPage("/sign-in")
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleUsers))
                        // Back to where sign-in was needed (an AI's authorization request), else /account.
                        .defaultSuccessUrl("/account")
                        .failureUrl("/sign-in?error"))
                .webAuthn(passkeys -> passkeys
                        .rpName("Kitchen")
                        .rpId(kitchen.rpId())
                        .allowedOrigins(kitchen.origin())
                        .disableDefaultRegistrationPage(true))
                // The React site reads the XSRF-TOKEN cookie and sends it back in the X-XSRF-TOKEN
                // header: a third-party site can trigger requests but cannot read the cookie.
                .csrf(csrf -> csrf.spa())
                // CORS preflights (OPTIONS) of the authorization server endpoints land here, because
                // that chain only matches the endpoints' real methods. Dev origins only, see DevClientCors.
                .cors(cors -> cors.configurationSource(DevClientCors.source(kitchen)))
                // An API call without a session gets 401, never a redirect to a login page.
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Only the authorization server chain remembers where to go back after sign-in.
                // An API call that got 401 here must never become the page shown after sign-in.
                .requestCache(cache -> cache.requestCache(readOnlyRequestCache()))
                .logout(logout -> logout.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                // Invitation links carry a token: never send the full URL to another site.
                .headers(headers -> headers.referrerPolicy(referrer -> referrer.policy(SAME_ORIGIN)));
        return http.build();
    }

    private static HttpSessionRequestCache readOnlyRequestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(request -> false);
        return cache;
    }

    /** Passkeys stored in PostgreSQL (tables from migration V3) instead of in memory. */
    @Bean
    PublicKeyCredentialUserEntityRepository passkeyUserEntities(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    UserCredentialRepository passkeyCredentials(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }
}
