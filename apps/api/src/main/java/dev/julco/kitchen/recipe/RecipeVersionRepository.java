package dev.julco.kitchen.recipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface RecipeVersionRepository extends Repository<RecipeVersion, UUID> {

    RecipeVersion save(RecipeVersion version);

    Optional<RecipeVersion> findByHouseholdIdAndRecipeIdAndVersion(UUID householdId, UUID recipeId, int version);

    List<RecipeVersion> findAllByHouseholdIdAndRecipeIdOrderByVersionDesc(UUID householdId, UUID recipeId);

    /** Keeps the history bounded: versions older than {@code keepFrom} go (CLAUDE.md rule 4). */
    @Modifying
    @Query("""
            delete from RecipeVersion v
            where v.householdId = :householdId and v.recipeId = :recipeId and v.version < :keepFrom""")
    int deleteOlderByHouseholdId(UUID householdId, UUID recipeId, int keepFrom);
}
