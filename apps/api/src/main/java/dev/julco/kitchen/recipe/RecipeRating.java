package dev.julco.kitchen.recipe;

import dev.julco.kitchen.common.HouseholdScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A person's rating of a household recipe, out of 10 (9 = perfect, 10 = best ever tasted).
 * Personal data: visible to the household, deleted when the person leaves it.
 */
@Entity
@Table(name = "recipe_rating")
public class RecipeRating implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    @Column(nullable = false, updatable = false)
    private UUID recipeId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private short score;

    private String comment;

    private String adjustments;

    @Column(nullable = false)
    private Instant cookedAt;

    protected RecipeRating() {}

    public RecipeRating(UUID householdId, UUID recipeId, UUID userId, short score, Instant cookedAt) {
        this.householdId = householdId;
        this.recipeId = recipeId;
        this.userId = userId;
        this.score = score;
        this.cookedAt = cookedAt;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public short getScore() {
        return score;
    }

    public String getComment() {
        return comment;
    }

    public String getAdjustments() {
        return adjustments;
    }

    public Instant getCookedAt() {
        return cookedAt;
    }
}
