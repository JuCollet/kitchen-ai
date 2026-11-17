package dev.julco.kitchen.menu;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface MenuItemRepository extends Repository<MenuItem, UUID> {

    MenuItem save(MenuItem item);

    List<MenuItem> findAllByHouseholdIdAndMenuIdOrderByServedOn(UUID householdId, UUID menuId);

    void deleteAllByHouseholdIdAndMenuId(UUID householdId, UUID menuId);
}
