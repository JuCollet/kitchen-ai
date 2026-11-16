package dev.julco.kitchen.auth;

import java.util.UUID;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * A Google user whose principal name is the Kitchen user id, not Google's subject. Every
 * authentication (Google or passkey) then exposes the same name: the Kitchen user id.
 */
class KitchenOidcUser extends DefaultOidcUser {

    private final UUID userId;

    KitchenOidcUser(OidcUser googleUser, UUID userId) {
        super(googleUser.getAuthorities(), googleUser.getIdToken(), googleUser.getUserInfo());
        this.userId = userId;
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
