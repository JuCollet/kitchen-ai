package dev.julco.kitchen.household;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface HouseholdInvitationRepository extends Repository<HouseholdInvitation, UUID> {

    HouseholdInvitation save(HouseholdInvitation invitation);

    List<HouseholdInvitation> findAllByHouseholdId(UUID householdId);
}
