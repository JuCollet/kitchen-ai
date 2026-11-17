package dev.julco.kitchen.profile;

import java.util.List;

/** The household profile, shared by all members. */
public record HouseholdProfileView(
        Short tableSize,
        List<String> goals,
        String goalsNote,
        String budget,
        Short maxActiveMinutes,
        List<String> staples,
        String notes,
        boolean onboardingComplete) {}
