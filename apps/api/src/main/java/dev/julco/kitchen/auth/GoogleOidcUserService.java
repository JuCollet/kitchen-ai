package dev.julco.kitchen.auth;

import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.user.AppUser;
import dev.julco.kitchen.user.EmailAlreadyUsedException;
import dev.julco.kitchen.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Locale;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
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
            return new KitchenOidcUser(googleUser, user.getId());
        } catch (EmailAlreadyUsedException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_already_used"));
        }
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
