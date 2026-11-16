package dev.julco.kitchen.recipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Published recipes are visible to every household; withdrawn ones are never returned. */
public interface SharedRecipeRepository extends Repository<SharedRecipe, UUID> {

    SharedRecipe save(SharedRecipe sharedRecipe);

    Optional<SharedRecipe> findByIdAndWithdrawnAtIsNull(UUID id);

    List<SharedRecipe> findAllByWithdrawnAtIsNullOrderByPublishedAtDesc();
}
