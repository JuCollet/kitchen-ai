package dev.julco.kitchen.shopping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Changes to an item of a list: only the fields given change. */
public record ShoppingItemEdit(
        @JsonProperty(required = true) @JsonPropertyDescription("Item id, from get_shopping_list.")
        @NotNull UUID itemId,
        @JsonProperty @JsonPropertyDescription("New name. Max 100 characters.")
        @Size(min = 1, max = 100) String name,
        @JsonProperty @JsonPropertyDescription("New total quantity.")
        @Positive @DecimalMax("100000") BigDecimal quantity,
        @JsonProperty @JsonPropertyDescription("New unit.")
        @Size(max = 30) String unit,
        @JsonProperty @JsonPropertyDescription("New store aisle code.")
        @Pattern(regexp = "produce|frozen|meat_fish|dairy|bakery|dry_goods|canned|condiments|drinks|household|other",
                message = "must be one of produce, frozen, meat_fish, dairy, bakery, dry_goods, canned, condiments, "
                        + "drinks, household, other")
        String aisle) {}
