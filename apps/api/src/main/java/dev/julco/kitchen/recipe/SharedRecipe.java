package dev.julco.kitchen.recipe;

import dev.julco.kitchen.common.Language;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * A recipe voluntarily published by a household. Deliberately not household-scoped for reading:
 * every household can discover it. Only the origin household can withdraw it.
 */
@Entity
@Table(name = "shared_recipe")
public class SharedRecipe {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID originHouseholdId;

    private UUID sourceRecipeId;

    @Column(nullable = false, updatable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Language language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private SharedRecipeContent content;

    private UUID publishedBy;

    @Column(nullable = false, updatable = false)
    private Instant publishedAt;

    private Instant withdrawnAt;

    /** Null: anonymous. */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private Signature signature;

    protected SharedRecipe() {}

    public SharedRecipe(
            UUID originHouseholdId,
            UUID sourceRecipeId,
            String title,
            Language language,
            SharedRecipeContent content,
            UUID publishedBy,
            Signature signature) {
        this.originHouseholdId = originHouseholdId;
        this.sourceRecipeId = sourceRecipeId;
        this.title = title;
        this.language = language;
        this.content = content;
        this.signature = signature;
        // Set here rather than by Hibernate at flush time: the tool result built right after
        // saving must already carry it.
        this.publishedAt = Instant.now();
        this.publishedBy = publishedBy;
    }

    void withdraw(Instant at) {
        this.withdrawnAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOriginHouseholdId() {
        return originHouseholdId;
    }

    public UUID getSourceRecipeId() {
        return sourceRecipeId;
    }

    public String getTitle() {
        return title;
    }

    public Language getLanguage() {
        return language;
    }

    public SharedRecipeContent getContent() {
        return content;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Signature getSignature() {
        return signature;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
