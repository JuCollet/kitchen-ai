package dev.julco.kitchen.auth;

import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.user.AuthenticatedUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The person behind the current request, whatever the authentication: browser session (Google or
 * passkey) or OAuth access token (AI client). In all cases the principal name is the Kitchen user
 * id: {@link GoogleOidcUserService#kitchenUser}, {@link PasskeyUserDetailsService}, and the "sub" claim of the access
 * tokens, which Spring Security sets to the name of the person who consented.
 */
@Component
public class CurrentUsers {

    private final HouseholdService households;

    CurrentUsers(HouseholdService households) {
        this.households = households;
    }

    public AuthenticatedUser authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Not signed in");
        }
        return new AuthenticatedUser(UUID.fromString(authentication.getName()));
    }

    /** Empty when the person does not belong to a household yet. */
    public Optional<CurrentMember> currentMember() {
        return households.resolveCurrentMember(authenticatedUser().userId());
    }
}
