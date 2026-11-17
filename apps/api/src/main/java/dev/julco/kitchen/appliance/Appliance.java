package dev.julco.kitchen.appliance;

import dev.julco.kitchen.common.HouseholdScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "appliance")
public class Appliance implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    @Column(nullable = false)
    private String name;

    private String brandModel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private ApplianceSpec spec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UsagePreference usagePreference = UsagePreference.SUGGEST;

    private Instant confirmedAt;

    private UUID confirmedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Appliance() {}

    public Appliance(UUID householdId, String name, String brandModel, ApplianceSpec spec) {
        this.householdId = householdId;
        this.name = name;
        this.brandModel = brandModel;
        this.spec = spec;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    public String getName() {
        return name;
    }

    public String getBrandModel() {
        return brandModel;
    }

    public ApplianceSpec getSpec() {
        return spec;
    }

    public UsagePreference getUsagePreference() {
        return usagePreference;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public UUID getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void replace(String name, String brandModel, ApplianceSpec spec) {
        this.name = name;
        this.brandModel = brandModel;
        this.spec = spec;
    }

    void confirm(UUID userId, Instant at) {
        this.confirmedBy = userId;
        this.confirmedAt = at;
    }

    void setUsagePreference(UsagePreference usagePreference) {
        this.usagePreference = usagePreference;
    }
}
