package dev.julco.kitchen.pantry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An item to add to the pantry, or to update if the household already has one with this name
 * (case does not matter). The aisle is a plain code, not the {@code Aisle} enum (see
 * IngredientInput).
 */
public record PantryUpsert(
        @JsonProperty(required = true) @JsonPropertyDescription("Ingredient name, for example 'basmati rice'. Max 100 characters.")
        @NotBlank @Size(max = 100) String name,
        @JsonProperty @JsonPropertyDescription("Approximate quantity in words, for example 'half a bag' or 'about 500 g'. "
                + "Omit to keep the current quantity.")
        @Size(max = 100) String quantity,
        @JsonProperty
        @JsonPropertyDescription("Store aisle: produce, frozen, meat_fish, dairy, bakery, dry_goods, canned, "
                + "condiments, drinks, household or other. Omit to keep the current one (other for a new item).")
        @Pattern(regexp = "produce|frozen|meat_fish|dairy|bakery|dry_goods|canned|condiments|drinks|household|other",
                message = "must be one of produce, frozen, meat_fish, dairy, bakery, dry_goods, canned, condiments, "
                        + "drinks, household, other")
        String aisle) {}
