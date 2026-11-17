package dev.julco.kitchen.profile;

import dev.julco.kitchen.user.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consent for health data (allergies), given or withdrawn by the person on the site, never by an
 * AI: the consent page of the OAuth flow and the account page call it. Withdrawing erases the
 * allergies.
 */
@RestController
@RequestMapping("/api/me/health-data-consent")
class HealthDataConsentController {

    private final ProfileService profiles;

    HealthDataConsentController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    ConsentResponse get(AuthenticatedUser authenticatedUser) {
        return new ConsentResponse(profiles.hasHealthDataConsent(authenticatedUser));
    }

    @PutMapping
    ConsentResponse set(AuthenticatedUser authenticatedUser, @Valid @RequestBody ConsentRequest request) {
        if (request.consent()) {
            profiles.giveHealthDataConsent(authenticatedUser);
        } else {
            profiles.withdrawHealthDataConsent(authenticatedUser);
        }
        return get(authenticatedUser);
    }

    record ConsentRequest(@NotNull Boolean consent) {}

    record ConsentResponse(boolean consent) {}
}
