package dev.julco.kitchen.pantry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface PantryItemRepository extends Repository<PantryItem, UUID> {

    PantryItem save(PantryItem item);

    Optional<PantryItem> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<PantryItem> findAllByHouseholdIdOrderByName(UUID householdId);
}
