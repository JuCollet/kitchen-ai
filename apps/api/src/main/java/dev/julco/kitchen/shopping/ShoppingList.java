package dev.julco.kitchen.shopping;

import dev.julco.kitchen.common.HouseholdScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "shopping_list")
public class ShoppingList implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    private UUID menuId;

    @Column(nullable = false)
    private String title;

    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ShoppingList() {}

    public ShoppingList(UUID householdId, UUID menuId, String title, UUID createdBy) {
        this.householdId = householdId;
        this.menuId = menuId;
        this.title = title;
        this.createdBy = createdBy;
        // Set here rather than by Hibernate at flush time: the tool result built right after
        // saving must already carry it.
        this.createdAt = Instant.now();
    }

    public void rename(String title) {
        this.title = title;
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

    public String getTitle() {
        return title;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
