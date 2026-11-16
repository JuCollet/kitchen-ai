package dev.julco.kitchen.recipe;

import dev.julco.kitchen.common.HouseholdScoped;
import dev.julco.kitchen.common.Language;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "recipe")
public class Recipe implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    /** Null for a classic recipe (pan, pot, oven). */
    private UUID applianceId;

    private UUID authorId;

    @Column(nullable = false)
    private String title;

    private String summary;

    @Column(nullable = false)
    private short servings;

    private Short activeMinutes;

    private Short totalMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Ingredient> ingredients = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<RecipeStep> steps = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> tags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private NutritionEstimate nutritionEstimate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> benefits = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language;

    @Column(nullable = false)
    private int version = 1;

    private String source;

    private UUID sharedRecipeId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Recipe() {}

    public Recipe(
            UUID householdId,
            UUID applianceId,
            UUID authorId,
            String title,
            short servings,
            List<Ingredient> ingredients,
            List<RecipeStep> steps,
            Language language) {
        this.householdId = householdId;
        this.applianceId = applianceId;
        this.authorId = authorId;
        this.title = title;
        this.servings = servings;
        this.ingredients = new ArrayList<>(ingredients);
        this.steps = new ArrayList<>(steps);
        this.language = language;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getApplianceId() {
        return applianceId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public short getServings() {
        return servings;
    }

    public Short getActiveMinutes() {
        return activeMinutes;
    }

    public Short getTotalMinutes() {
        return totalMinutes;
    }

    public List<Ingredient> getIngredients() {
        return List.copyOf(ingredients);
    }

    public List<RecipeStep> getSteps() {
        return List.copyOf(steps);
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public NutritionEstimate getNutritionEstimate() {
        return nutritionEstimate;
    }

    public List<String> getBenefits() {
        return List.copyOf(benefits);
    }

    public Language getLanguage() {
        return language;
    }

    public int getVersion() {
        return version;
    }

    public String getSource() {
        return source;
    }

    public UUID getSharedRecipeId() {
        return sharedRecipeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
