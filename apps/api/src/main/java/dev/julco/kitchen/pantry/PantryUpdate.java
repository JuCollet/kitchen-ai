package dev.julco.kitchen.pantry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Changes to the pantry in one call: items to add or update, and names to remove. */
public record PantryUpdate(
        @Size(max = 100) List<@NotNull @Valid PantryUpsert> upsert,
        @Size(max = 100) List<@NotBlank @Size(max = 100) String> remove) {

    public PantryUpdate {
        upsert = upsert == null ? List.of() : upsert;
        remove = remove == null ? List.of() : remove;
    }
}
