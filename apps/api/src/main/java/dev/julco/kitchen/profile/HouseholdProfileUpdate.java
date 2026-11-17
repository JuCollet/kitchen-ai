package dev.julco.kitchen.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Changes to the household profile. Same conventions as {@link PersonProfileUpdate}. */
public record HouseholdProfileUpdate(
        @Min(1) @Max(20) Integer tableSize,
        @Size(max = 10) List<@NotBlank @Size(max = 100) String> goals,
        @Size(max = 1000) String goalsNote,
        @Size(max = 200) String budget,
        @Min(1) @Max(600) Integer maxActiveMinutes,
        @Size(max = 50) List<@NotBlank @Size(max = 100) String> staples,
        @Size(max = 2000) String notes,
        Boolean onboardingComplete) {}
