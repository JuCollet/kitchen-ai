package dev.julco.kitchen.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.user.AppUser;
import jakarta.persistence.EntityManager;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.transaction.annotation.Transactional;

/**
 * During an AI's authorization request, Spring Security stores the signed-in person (the
 * Authentication) as JSON in oauth2_authorization, and reads it back at consent. This only works
 * for classes it knows: checked here for both ways of signing in, with the real principal types.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PrincipalStorageTest {

    @Autowired EntityManager em;
    @Autowired OAuth2AuthorizationService authorizations;
    @Autowired RegisteredClientRepository clients;

    @Test
    void a_google_sign_in_survives_storage() {
        AppUser user = HouseholdFixture.createUser(em, "Nina");
        OidcUser google = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("OIDC_USER"),
                new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60),
                        Map.of("sub", "google-sub", "email", "nina@example.test")),
                new OidcUserInfo(Map.of("sub", "google-sub", "email", "nina@example.test")),
                "sub");
        OidcUser kitchenUser = GoogleOidcUserService.kitchenUser(google, user.getId());

        Authentication restored = storeAndReload(
                new OAuth2AuthenticationToken(kitchenUser, kitchenUser.getAuthorities(), "google"));

        assertThat(restored.getName()).isEqualTo(user.getId().toString());
    }

    @Test
    void a_passkey_sign_in_survives_storage() {
        AppUser user = HouseholdFixture.createUser(em, "Nina");
        var passkeyUser = ImmutablePublicKeyCredentialUserEntity.builder()
                .name(user.getId().toString())
                .id(Bytes.random())
                .displayName("Nina")
                .build();

        Authentication restored = storeAndReload(
                new WebAuthnAuthentication(passkeyUser, AuthorityUtils.createAuthorityList("ROLE_USER")));

        assertThat(restored.getName()).isEqualTo(user.getId().toString());
    }

    private Authentication storeAndReload(Authentication principal) {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(UUID.randomUUID().toString())
                .clientName("Test AI")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://ai.example/callback")
                .scopes(scopes -> scopes.addAll(List.of("kitchen")))
                .build();
        clients.save(client);
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName(principal.getName())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(Principal.class.getName(), principal)
                .build();
        authorizations.save(authorization);

        OAuth2Authorization reloaded = authorizations.findById(authorization.getId());
        return reloaded.getAttribute(Principal.class.getName());
    }
}
