package dev.julco.kitchen.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * An ingredient as written by the AI. The aisle is a plain code here, not the {@code Aisle} enum:
 * the MCP schema generator would list the Java constant names (PRODUCE) instead of the codes
 * clients must send (produce). The Jackson annotations describe the MCP tool schema.
 */
public record IngredientInput(
        @JsonProperty(required = true) @JsonPropertyDescription("Ingredient name, for example 'chicken thighs'.")
        @NotBlank @Size(max = 100) String name,
        @JsonProperty @JsonPropertyDescription("Quantity for the recipe's servings; omit for 'to taste'.")
        @Positive @DecimalMax("100000") BigDecimal quantity,
        @JsonProperty @JsonPropertyDescription("Unit, for example g, ml, tbsp, piece. Omit for a count.")
        @Size(max = 30) String unit,
        @JsonProperty
        @JsonPropertyDescription("Store aisle: produce, frozen, meat_fish, dairy, bakery, dry_goods, canned, "
                + "condiments, drinks, household or other. Defaults to other.")
        @Pattern(regexp = "produce|frozen|meat_fish|dairy|bakery|dry_goods|canned|condiments|drinks|household|other",
                message = "must be one of produce, frozen, meat_fish, dairy, bakery, dry_goods, canned, condiments, "
                        + "drinks, household, other")
        String aisle,
        @JsonProperty @JsonPropertyDescription("Preparation or remark, for example 'finely chopped'.")
        @Size(max = 200) String note) {}
