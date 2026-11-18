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
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
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
            HttpSecurity http,
            GoogleOidcUserService googleUsers,
            KitchenProperties kitchen,
            ClientRegistrationRepository clientRegistrations) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error", "/api/csrf").permitAll()
                        // Menu PDF links: the random token in the URL is the credential (opened from an
                        // AI app, without a session). Unknown or expired token: 404.
                        .requestMatchers(HttpMethod.GET, "/exports/*").permitAll()
                        // Passkey sign-in: the person is not authenticated yet.
                        .requestMatchers(HttpMethod.POST, "/webauthn/authenticate/options", "/login/webauthn")
                        .permitAll()
                        // Everything else, including passkey registration, needs a signed-in person.
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        // Sign-in page of the React site; Spring's generated page is never used.
                        .loginPage("/sign-in")
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(chooseGoogleAccount(clientRegistrations)))
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
                // Ends the Kitchen session only: the Google session is the person's own business.
                .logout(logout -> logout
                        .deleteCookies("KITCHEN_SESSION")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                // Invitation links carry a token: never send the full URL to another site.
                .headers(headers -> headers.referrerPolicy(referrer -> referrer.policy(SAME_ORIGIN)));
        return http.build();
    }

    /**
     * Signing out of Kitchen does not sign out of Google. Without a prompt, Google answers the
     * next sign-in silently with the account still open in the browser, which looks as if the
     * sign-out had not worked, and makes it impossible to pick another account (a second household
     * member on a shared computer). {@code prompt=select_account} always shows Google's account
     * chooser; it does not ask for the Google password again.
     */
    private static OAuth2AuthorizationRequestResolver chooseGoogleAccount(ClientRegistrationRepository clientRegistrations) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(request -> request.additionalParameters(
                parameters -> parameters.put("prompt", "select_account")));
        return resolver;
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
