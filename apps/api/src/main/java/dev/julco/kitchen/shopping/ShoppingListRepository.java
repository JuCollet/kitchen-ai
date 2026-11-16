package dev.julco.kitchen.shopping;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ShoppingListRepository extends Repository<ShoppingList, UUID> {

    ShoppingList save(ShoppingList list);

    Optional<ShoppingList> findByIdAndHouseholdId(UUID id, UUID householdId);

    Optional<ShoppingList> findFirstByHouseholdIdOrderByCreatedAtDesc(UUID householdId);
}
