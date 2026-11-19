package dev.julco.kitchen.recipe;

import dev.julco.kitchen.common.HouseholdScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** One saved version of a recipe, kept to go back to it (V11). Never modified once written. */
@Entity
@Table(name = "recipe_version")
public class RecipeVersion implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    @Column(nullable = false, updatable = false)
    private UUID recipeId;

    @Column(nullable = false, updatable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private RecipeSnapshot content;

    @Column(updatable = false)
    private String changeNote;

    @Column(updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected RecipeVersion() {}

    RecipeVersion(UUID householdId, UUID recipeId, int version, RecipeSnapshot content, String changeNote, UUID createdBy) {
        this.householdId = householdId;
        this.recipeId = recipeId;
        this.version = version;
        this.content = content;
        this.changeNote = changeNote;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public int getVersion() {
        return version;
    }

    public RecipeSnapshot getContent() {
        return content;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
