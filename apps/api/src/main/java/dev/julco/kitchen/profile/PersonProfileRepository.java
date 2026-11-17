package dev.julco.kitchen.profile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Personal data: always filtered by the current member's user id (CLAUDE.md rule 1). */
public interface PersonProfileRepository extends Repository<PersonProfile, UUID> {

    PersonProfile save(PersonProfile profile);

    Optional<PersonProfile> findByUserId(UUID userId);

    /** The callers pass the ids of the current household's members only (ProfileService). */
    List<PersonProfile> findAllByUserIdIn(Collection<UUID> userIds);
}
