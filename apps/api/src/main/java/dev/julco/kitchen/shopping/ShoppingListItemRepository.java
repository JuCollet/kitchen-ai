package dev.julco.kitchen.shopping;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ShoppingListItemRepository extends Repository<ShoppingListItem, UUID> {

    ShoppingListItem save(ShoppingListItem item);

    List<ShoppingListItem> findAllByHouseholdIdAndListIdOrderByPosition(UUID householdId, UUID listId);

    void deleteByHouseholdIdAndId(UUID householdId, UUID id);
}
