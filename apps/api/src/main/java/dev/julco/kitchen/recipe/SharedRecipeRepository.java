package dev.julco.kitchen.recipe;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Published recipes are visible to every household; withdrawn ones are never returned. */
public interface SharedRecipeRepository extends Repository<SharedRecipe, UUID> {

    SharedRecipe save(SharedRecipe sharedRecipe);

    Optional<SharedRecipe> findByIdAndWithdrawnAtIsNull(UUID id);

    List<SharedRecipe> findAllByWithdrawnAtIsNullOrderByPublishedAtDesc();

    /** Withdraws the live publication of a recipe of the household; returns 0 or 1. */
    @Modifying
    @Query("""
            update SharedRecipe s set s.withdrawnAt = :at
            where s.sourceRecipeId = :recipeId and s.originHouseholdId = :householdId and s.withdrawnAt is null""")
    int withdrawLive(UUID recipeId, UUID householdId, Instant at);

    /** The live publication of a recipe of the household, if any. */
    Optional<SharedRecipe> findBySourceRecipeIdAndOriginHouseholdIdAndWithdrawnAtIsNull(
            UUID sourceRecipeId, UUID originHouseholdId);
}
