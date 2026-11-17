package dev.julco.kitchen.menu;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface MenuRepository extends Repository<Menu, UUID> {

    Menu save(Menu menu);

    Optional<Menu> findByIdAndHouseholdId(UUID id, UUID householdId);

    Optional<Menu> findFirstByHouseholdIdOrderByStartsOnDescCreatedAtDesc(UUID householdId);
}
