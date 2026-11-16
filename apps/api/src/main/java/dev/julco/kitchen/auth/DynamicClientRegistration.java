package dev.julco.kitchen.auth;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2ClientRegistration;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.converter.OAuth2ClientRegistrationRegisteredClientConverter;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.core.convert.converter.Converter;

/**
 * Rules for AI clients that register themselves (RFC 7591). Registration is open: anyone can
 * register a client, so a client gets nothing by registering. It still needs a person to sign in
 * and explicitly consent, on a page that shows where the tokens will be sent.
 */
final class DynamicClientRegistration {

    /** The only scope: full access to the person's household, through the MCP tools. */
    static final String SCOPE = "kitchen";

    static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(1);
    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private DynamicClientRegistration() {}

    /** Whatever the client asks for, it gets these settings. */
    static Converter<OAuth2ClientRegistration, RegisteredClient> converter() {
        OAuth2ClientRegistrationRegisteredClientConverter defaults = new OAuth2ClientRegistrationRegisteredClientConverter();
        return registration -> RegisteredClient.from(defaults.convert(registration))
                .authorizationGrantTypes(grantTypes -> {
                    grantTypes.clear();
                    grantTypes.add(AuthorizationGrantType.AUTHORIZATION_CODE);
                    grantTypes.add(AuthorizationGrantType.REFRESH_TOKEN);
                })
                .scopes(scopes -> {
                    scopes.clear();
                    scopes.add(SCOPE);
                })
                .clientSettings(ClientSettings.builder()
                        // PKCE always, consent page always.
                        .requireProofKey(true)
                        .requireAuthorizationConsent(true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                        .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                        // A refresh token is used once, then replaced: a stolen one stops working.
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }

    /** Spring Security's checks, plus: tokens only go to HTTPS addresses (or to this computer). */
    static Consumer<OAuth2ClientRegistrationAuthenticationContext> validator() {
        return OAuth2ClientRegistrationAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR
                .andThen(OAuth2ClientRegistrationAuthenticationValidator.DEFAULT_JWK_SET_URI_VALIDATOR)
                .andThen(DynamicClientRegistration::httpsOrLoopbackRedirectUris);
    }

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private static void httpsOrLoopbackRedirectUris(OAuth2ClientRegistrationAuthenticationContext context) {
        OAuth2ClientRegistrationAuthenticationToken authentication = context.getAuthentication();
        var redirectUris = authentication.getClientRegistration().getRedirectUris();
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw invalidRedirectUri();
        }
        for (String redirectUri : redirectUris) {
            URI uri;
            try {
                uri = URI.create(redirectUri);
            } catch (IllegalArgumentException e) {
                throw invalidRedirectUri();
            }
            boolean https = "https".equals(uri.getScheme());
            boolean loopback = "http".equals(uri.getScheme()) && LOOPBACK_HOSTS.contains(uri.getHost());
            if (!https && !loopback) {
                throw invalidRedirectUri();
            }
        }
    }

    private static OAuth2AuthenticationException invalidRedirectUri() {
        return new OAuth2AuthenticationException(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_REDIRECT_URI, "Redirect URIs must use https (or http on localhost)", null));
    }
}
