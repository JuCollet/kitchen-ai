package dev.julco.kitchen.pantry;

import dev.julco.kitchen.common.Aisle;
import dev.julco.kitchen.common.HouseholdScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "pantry_item")
public class PantryItem implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    @Column(nullable = false)
    private String name;

    /** Approximate, free text ("half a bag"). */
    private String quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Aisle aisle = Aisle.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PantrySource source;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected PantryItem() {}

    public PantryItem(UUID householdId, String name, String quantity, Aisle aisle, PantrySource source) {
        this.householdId = householdId;
        this.name = name;
        this.quantity = quantity;
        this.aisle = aisle;
        this.source = source;
    }

    /** Updates the item; a null quantity or aisle keeps the current one. */
    public void update(String quantity, Aisle aisle, PantrySource source) {
        if (quantity != null) {
            this.quantity = quantity;
        }
        if (aisle != null) {
            this.aisle = aisle;
        }
        this.source = source;
    }

    /** Puts back the quantity an update replaced, even an unknown (null) one. */
    public void restoreQuantity(String quantity) {
        this.quantity = quantity;
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

    public String getQuantity() {
        return quantity;
    }

    public Aisle getAisle() {
        return aisle;
    }

    public PantrySource getSource() {
        return source;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
