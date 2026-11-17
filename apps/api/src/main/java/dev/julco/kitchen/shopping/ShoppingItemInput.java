package dev.julco.kitchen.shopping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * An item to buy, as written by the AI. The aisle is a plain code, not the {@code Aisle} enum (see
 * IngredientInput).
 */
public record ShoppingItemInput(
        @JsonProperty(required = true) @JsonPropertyDescription("What to buy, for example 'chicken thighs'. Max 100 characters.")
        @NotBlank @Size(max = 100) String name,
        @JsonProperty @JsonPropertyDescription("Total quantity to buy; omit for a count-free item.")
        @Positive @DecimalMax("100000") BigDecimal quantity,
        @JsonProperty @JsonPropertyDescription("Unit, for example g, ml, piece. Omit for a count.")
        @Size(max = 30) String unit,
        @JsonProperty
        @JsonPropertyDescription("Store aisle: produce, frozen, meat_fish, dairy, bakery, dry_goods, canned, "
                + "condiments, drinks, household or other. Defaults to other.")
        @Pattern(regexp = "produce|frozen|meat_fish|dairy|bakery|dry_goods|canned|condiments|drinks|household|other",
                message = "must be one of produce, frozen, meat_fish, dairy, bakery, dry_goods, canned, condiments, "
                        + "drinks, household, other")
        String aisle) {}
