package dev.julco.kitchen.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Identity, not household data: a user is only ever loaded by their own id, taken from the
 * current session or token.
 */
public interface AppUserRepository extends Repository<AppUser, UUID> {

    AppUser save(AppUser user);

    Optional<AppUser> findById(UUID userId);
}
