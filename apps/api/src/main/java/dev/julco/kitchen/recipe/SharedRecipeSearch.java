package dev.julco.kitchen.recipe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Criteria to search the recipes other households published, all optional. {@code author}: part of
 * the name a publication is signed with (household or person), accents and case ignored.
 */
public record SharedRecipeSearch(
        @Size(max = 200) String text,
        @Size(max = 10) List<@NotBlank @Size(max = 40) String> tags,
        @Pattern(regexp = "fr|nl|en", message = "must be fr, nl or en") String language,
        @Size(max = 100) String author,
        @Min(0) @Max(100) Integer page) {

    static final int PAGE_SIZE = 10;

    int pageOrFirst() {
        return page == null ? 0 : page;
    }
}
