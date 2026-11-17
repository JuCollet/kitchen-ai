package dev.julco.kitchen.recipe;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Search criteria, all optional and combined with AND. The household is never a criterion: it
 * always comes from the current member.
 */
public record RecipeSearch(
        @Size(max = 200) String text,
        @Size(max = 10) List<@NotBlank @Size(max = 40) String> tags,
        UUID applianceId,
        Boolean classicOnly,
        @Min(1) @Max(10) Integer minMyScore,
        @Min(1) @Max(10) Integer minHouseholdScore,
        @Min(1) @Max(3650) Integer notCookedForDays,
        @Min(0) @Max(100) Integer page,
        @Min(1) @Max(20) Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 10;

    @AssertTrue(message = "applianceId and classicOnly cannot be combined")
    boolean isApplianceFilterConsistent() {
        return applianceId == null || !Boolean.TRUE.equals(classicOnly);
    }

    int pageOrFirst() {
        return page == null ? 0 : page;
    }

    int sizeOrDefault() {
        return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
