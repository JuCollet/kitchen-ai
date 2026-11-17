package dev.julco.kitchen.profile;

import dev.julco.kitchen.common.Language;
import java.util.List;

/** The current person's profile, as shown to them and to their AI. */
public record PersonProfileView(
        String displayName,
        Language language,
        String diet,
        List<String> allergies,
        List<String> dislikes,
        String notes,
        boolean healthDataConsent,
        boolean onboardingComplete) {}
