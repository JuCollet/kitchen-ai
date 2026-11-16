package dev.julco.kitchen.user;

import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.common.NotFoundException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Kitchen accounts. An account is created at the first Google sign-in. */
@Service
@Validated
@Transactional
public class UserService {

    private final AppUserRepository users;
    private final FederatedIdentityRepository identities;

    public UserService(AppUserRepository users, FederatedIdentityRepository identities) {
        this.users = users;
        this.identities = identities;
    }

    /**
     * Returns the account linked to this Google account, creating it at the first sign-in.
     * The caller must have checked that Google verified the email.
     */
    public AppUser signInWithGoogle(
            @NotBlank @Size(max = 255) String subject,
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 200) String displayName,
            @NotNull Language locale) {
        return identities.findByProviderAndSubject(FederatedIdentity.GOOGLE, subject)
                .map(identity -> users.findById(identity.getUserId()).orElseThrow())
                .orElseGet(() -> createFromGoogle(subject, email, displayName, locale));
    }

    @Transactional(readOnly = true)
    public AppUser getUser(@NotNull AuthenticatedUser authenticatedUser) {
        return users.findById(authenticatedUser.userId()).orElseThrow(NotFoundException::new);
    }

    private AppUser createFromGoogle(String subject, String email, String displayName, Language locale) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException();
        }
        AppUser user = users.save(new AppUser(email, displayNameOrDefault(displayName, email), locale));
        identities.save(new FederatedIdentity(FederatedIdentity.GOOGLE, subject, user.getId()));
        return user;
    }

    private static String displayNameOrDefault(String displayName, String email) {
        String name = displayName == null || displayName.isBlank() ? email.substring(0, email.indexOf('@')) : displayName;
        name = name.strip();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
