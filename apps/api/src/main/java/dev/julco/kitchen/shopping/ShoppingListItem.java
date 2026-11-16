package dev.julco.kitchen.shopping;

import dev.julco.kitchen.common.Aisle;
import dev.julco.kitchen.common.HouseholdScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "shopping_list_item")
public class ShoppingListItem implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    @Column(nullable = false, updatable = false)
    private UUID listId;

    @Column(nullable = false)
    private String name;

    private BigDecimal quantity;

    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Aisle aisle = Aisle.OTHER;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean checked;

    private UUID checkedBy;

    private Instant checkedAt;

    protected ShoppingListItem() {}

    public ShoppingListItem(
            UUID householdId, UUID listId, String name, BigDecimal quantity, String unit, Aisle aisle, int position) {
        this.householdId = householdId;
        this.listId = listId;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.aisle = aisle;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getListId() {
        return listId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public Aisle getAisle() {
        return aisle;
    }

    public int getPosition() {
        return position;
    }

    public boolean isChecked() {
        return checked;
    }

    public UUID getCheckedBy() {
        return checkedBy;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
