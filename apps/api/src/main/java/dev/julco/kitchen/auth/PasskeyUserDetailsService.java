package dev.julco.kitchen.auth;

import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.user.AuthenticatedUser;
import dev.julco.kitchen.user.UserService;
import java.util.UUID;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Used by Spring Security after a passkey sign-in: the passkey's user name is the Kitchen user id.
 * There are no passwords in Kitchen; this service is never used for password sign-in.
 */
@Component
class PasskeyUserDetailsService implements UserDetailsService {

    private final UserService users;

    PasskeyUserDetailsService(UserService users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        try {
            UUID userId = UUID.fromString(username);
            users.getUser(new AuthenticatedUser(userId));
            return User.withUsername(userId.toString()).password("").authorities("ROLE_USER").build();
        } catch (IllegalArgumentException | NotFoundException e) {
            throw new UsernameNotFoundException("Unknown user");
        }
    }
}
