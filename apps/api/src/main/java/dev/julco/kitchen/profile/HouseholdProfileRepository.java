package dev.julco.kitchen.profile;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface HouseholdProfileRepository extends Repository<HouseholdProfile, UUID> {

    HouseholdProfile save(HouseholdProfile profile);

    Optional<HouseholdProfile> findByHouseholdId(UUID householdId);
}
