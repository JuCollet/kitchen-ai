package dev.julco.kitchen.pantry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface PantryItemRepository extends Repository<PantryItem, UUID> {

    PantryItem save(PantryItem item);

    Optional<PantryItem> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<PantryItem> findAllByHouseholdIdOrderByName(UUID householdId);

    /** Same comparison as the unique index of V7: lower(name). */
    @Query("select p from PantryItem p where p.householdId = :householdId and lower(p.name) = lower(:name)")
    Optional<PantryItem> findByHouseholdIdAndNameIgnoringCase(UUID householdId, String name);

    void deleteByHouseholdIdAndId(UUID householdId, UUID id);

    long countByHouseholdId(UUID householdId);
}
