package dev.julco.kitchen.auth;

import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.user.AppUser;
import dev.julco.kitchen.user.EmailAlreadyUsedException;
import dev.julco.kitchen.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Called by Spring Security after Google sign-in: maps the Google account to a Kitchen account,
 * creating it at the first sign-in.
 */
@Component
class GoogleOidcUserService extends OidcUserService {

    private final UserService users;

    GoogleOidcUserService(UserService users) {
        this.users = users;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser googleUser = super.loadUser(request);
        if (!Boolean.TRUE.equals(googleUser.getEmailVerified())) {
            // An unverified email could belong to someone else.
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified"));
        }
        try {
            AppUser user = users.signInWithGoogle(
                    googleUser.getSubject(), googleUser.getEmail(), googleUser.getFullName(), browserLanguage());
            return kitchenUser(googleUser, user.getId());
        } catch (EmailAlreadyUsedException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_already_used"));
        }
    }

    /** Name of the extra attribute holding the Kitchen user id: the principal name of the session. */
    static final String KITCHEN_USER_ID = "kitchen_user_id";

    /**
     * The Google user, whose principal name is the Kitchen user id instead of Google's subject: every
     * authentication (Google or passkey) then exposes the same name.
     *
     * <p>Deliberately Spring's own {@link DefaultOidcUser}, not a subclass: the authorization server
     * stores the principal as JSON during an AI's authorization request, and can only read back
     * classes it knows.
     */
    static OidcUser kitchenUser(OidcUser googleUser, UUID userId) {
        Map<String, Object> claims = new HashMap<>(
                googleUser.getUserInfo() == null ? Map.of() : googleUser.getUserInfo().getClaims());
        claims.put(KITCHEN_USER_ID, userId.toString());
        return new DefaultOidcUser(
                googleUser.getAuthorities(), googleUser.getIdToken(), new OidcUserInfo(claims), KITCHEN_USER_ID);
    }

    /** Language of a new account: the first supported one in the browser preferences. */
    private static Language browserLanguage() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            for (Locale locale : Collections.list(request.getLocales())) {
                for (Language language : Language.values()) {
                    if (language.code().equals(locale.getLanguage())) {
                        return language;
                    }
                }
            }
        }
        return Language.EN;
    }
}
