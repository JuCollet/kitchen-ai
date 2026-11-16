package dev.julco.kitchen.household;

import dev.julco.kitchen.common.UnscopedQuery;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

public interface HouseholdInvitationRepository extends Repository<HouseholdInvitation, UUID> {

    HouseholdInvitation save(HouseholdInvitation invitation);

    List<HouseholdInvitation> findAllByHouseholdId(UUID householdId);

    /**
     * Locked until the end of the transaction, so that two people accepting the same invitation
     * at the same time cannot both join.
     */
    @UnscopedQuery("the token is the secret that grants access to the household")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HouseholdInvitation> findByTokenHash(byte[] tokenHash);
}
