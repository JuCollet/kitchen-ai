package dev.julco.kitchen.onboarding;

import java.util.List;

/**
 * Where the person and their household stand in the onboarding interview, and the questions left
 * to ask, in order. Empty when both parts are complete.
 */
public record OnboardingState(
        boolean personalComplete, boolean householdComplete, List<OnboardingQuestion> nextQuestions) {}
