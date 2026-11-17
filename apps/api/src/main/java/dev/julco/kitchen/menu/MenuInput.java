package dev.julco.kitchen.menu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A menu to create ({@code id} null) or to replace entirely. Every list and text is bounded
 * (CLAUDE.md rule 4); the period rules (at most 31 days, meals inside it) are checked by
 * {@link MenuService} with messages written for the AI.
 */
public record MenuInput(
        UUID id,
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @Size(max = 10) List<@NotNull UUID> requiredApplianceIds,
        @Size(max = 10) List<@NotNull UUID> excludedApplianceIds,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid MenuItemInput> items) {}
