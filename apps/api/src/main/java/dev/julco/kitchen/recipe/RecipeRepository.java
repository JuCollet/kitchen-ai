package dev.julco.kitchen.recipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface RecipeRepository extends Repository<Recipe, UUID> {

    Recipe save(Recipe recipe);

    Optional<Recipe> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<Recipe> findAllByHouseholdIdOrderByTitle(UUID householdId);
}
