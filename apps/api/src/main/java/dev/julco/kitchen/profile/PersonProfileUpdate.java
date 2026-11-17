package dev.julco.kitchen.profile;

import dev.julco.kitchen.common.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Changes to the personal profile. A null field is left unchanged; a blank text or an empty list
 * clears it. Lists are bounded, like every input (CLAUDE.md rule 4).
 */
public record PersonProfileUpdate(
        @Size(max = 200) String diet,
        @Size(max = 30) List<@NotBlank @Size(max = 100) String> allergies,
        @Size(max = 50) List<@NotBlank @Size(max = 100) String> dislikes,
        @Size(max = 2000) String notes,
        Language language,
        Boolean onboardingComplete) {}
