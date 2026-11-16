package dev.julco.kitchen.export;

import dev.julco.kitchen.common.HouseholdScoped;
import dev.julco.kitchen.common.Language;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "pdf_export")
public class PdfExport implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    @Column(nullable = false, updatable = false)
    private UUID menuId;

    private UUID requestedBy;

    /** SHA-256 of the download token; the token itself is never stored. */
    @Column(nullable = false, updatable = false)
    private byte[] tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Language language;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    protected PdfExport() {}

    public PdfExport(
            UUID householdId, UUID menuId, UUID requestedBy, byte[] tokenHash, Language language, Instant expiresAt) {
        this.householdId = householdId;
        this.menuId = menuId;
        this.requestedBy = requestedBy;
        this.tokenHash = tokenHash.clone();
        this.language = language;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getMenuId() {
        return menuId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Language getLanguage() {
        return language;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
