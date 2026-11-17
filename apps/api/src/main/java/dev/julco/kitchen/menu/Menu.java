package dev.julco.kitchen.menu;

import dev.julco.kitchen.common.HouseholdScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "menu")
public class Menu implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate startsOn;

    @Column(nullable = false)
    private LocalDate endsOn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private ApplianceOverrides applianceOverrides = ApplianceOverrides.NONE;

    private UUID createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Menu() {}

    public Menu(UUID householdId, String title, LocalDate startsOn, LocalDate endsOn, UUID createdBy) {
        this.householdId = householdId;
        this.title = title;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.createdBy = createdBy;
    }

    /** Replaces the whole menu (its items are replaced by the service). */
    public void replace(String title, LocalDate startsOn, LocalDate endsOn, ApplianceOverrides applianceOverrides) {
        this.title = title;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.applianceOverrides = applianceOverrides;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public ApplianceOverrides getApplianceOverrides() {
        return applianceOverrides;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
