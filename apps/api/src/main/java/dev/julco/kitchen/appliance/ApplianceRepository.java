package dev.julco.kitchen.appliance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ApplianceRepository extends Repository<Appliance, UUID> {

    Appliance save(Appliance appliance);

    Optional<Appliance> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<Appliance> findAllByHouseholdIdOrderByName(UUID householdId);
}
