package dev.julco.kitchen.shopping;

import dev.julco.kitchen.common.Aisle;
import dev.julco.kitchen.common.HouseholdScoped;
import dev.julco.kitchen.pantry.PantryLink;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
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

    /** What checking this item did to the pantry (V8), to undo it when it is unchecked. */
    private UUID pantryItemId;

    private Boolean pantryItemCreated;

    private String pantryPreviousQuantity;

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

    /** Edits the item; a null value keeps the current one. */
    public void edit(String name, BigDecimal quantity, String unit, Aisle aisle) {
        if (name != null) {
            this.name = name;
        }
        if (quantity != null) {
            this.quantity = quantity;
        }
        if (unit != null) {
            this.unit = unit;
        }
        if (aisle != null) {
            this.aisle = aisle;
        }
    }

    public void check(UUID userId, Instant at, PantryLink pantryLink) {
        this.checked = true;
        this.checkedBy = userId;
        this.checkedAt = at;
        if (pantryLink != null) {
            this.pantryItemId = pantryLink.pantryItemId();
            this.pantryItemCreated = pantryLink.created();
            this.pantryPreviousQuantity = pantryLink.previousQuantity();
        }
    }

    public void uncheck() {
        this.checked = false;
        this.checkedBy = null;
        this.checkedAt = null;
        this.pantryItemId = null;
        this.pantryItemCreated = null;
        this.pantryPreviousQuantity = null;
    }

    /** What checking this item did to the pantry, if anything. */
    public Optional<PantryLink> pantryLink() {
        return pantryItemId == null
                ? Optional.empty()
                : Optional.of(new PantryLink(pantryItemId, Boolean.TRUE.equals(pantryItemCreated), pantryPreviousQuantity));
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
