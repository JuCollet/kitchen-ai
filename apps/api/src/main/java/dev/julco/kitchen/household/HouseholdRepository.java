package dev.julco.kitchen.household;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** The household id always comes from {@link CurrentMember#householdId()}. */
public interface HouseholdRepository extends Repository<Household, UUID> {

    Household save(Household household);

    Optional<Household> findById(UUID householdId);

    void deleteById(UUID householdId);
}
