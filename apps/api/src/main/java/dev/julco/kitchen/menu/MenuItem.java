package dev.julco.kitchen.menu;

import dev.julco.kitchen.common.HouseholdScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "menu_item")
public class MenuItem implements HouseholdScoped {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID householdId;

    @Column(nullable = false, updatable = false)
    private UUID menuId;

    @Column(nullable = false)
    private LocalDate servedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Meal meal;

    @Column(nullable = false)
    private UUID recipeId;

    @Column(nullable = false)
    private short servings;

    protected MenuItem() {}

    public MenuItem(UUID householdId, UUID menuId, LocalDate servedOn, Meal meal, UUID recipeId, short servings) {
        this.householdId = householdId;
        this.menuId = menuId;
        this.servedOn = servedOn;
        this.meal = meal;
        this.recipeId = recipeId;
        this.servings = servings;
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

    public LocalDate getServedOn() {
        return servedOn;
    }

    public Meal getMeal() {
        return meal;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public short getServings() {
        return servings;
    }
}
