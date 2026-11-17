package dev.julco.kitchen.recipe;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface RecipeRatingRepository extends Repository<RecipeRating, UUID> {

    RecipeRating save(RecipeRating rating);

    List<RecipeRating> findAllByHouseholdIdAndRecipeIdOrderByCookedAtDescIdDesc(UUID householdId, UUID recipeId);
}
