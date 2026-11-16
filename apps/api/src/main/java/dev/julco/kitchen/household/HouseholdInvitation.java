package dev.julco.kitchen.household;

import dev.julco.kitchen.common.HouseholdScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "household_invitation")
public class HouseholdInvitation implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    /** SHA-256 of the token; the token itself is never stored. */
    @Column(nullable = false, updatable = false)
    private byte[] tokenHash;

    private UUID createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    private UUID usedBy;

    protected HouseholdInvitation() {}

    public HouseholdInvitation(UUID householdId, byte[] tokenHash, UUID createdBy, Instant expiresAt) {
        this.householdId = householdId;
        this.tokenHash = tokenHash.clone();
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public UUID getUsedBy() {
        return usedBy;
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(UUID userId, Instant now) {
        this.usedBy = userId;
        this.usedAt = now;
    }
}
