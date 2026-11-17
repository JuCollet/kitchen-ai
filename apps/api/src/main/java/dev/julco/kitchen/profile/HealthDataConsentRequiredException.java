package dev.julco.kitchen.profile;

import dev.julco.kitchen.common.InvalidInputException;

/**
 * Allergies are health data: they are only stored after the person consented on the Kitchen
 * website (docs/PLAN.md §8).
 */
public class HealthDataConsentRequiredException extends InvalidInputException {

    public HealthDataConsentRequiredException() {
        super("Allergies are health data: the person must first allow Kitchen to store them, on their "
                + "account page of the Kitchen website. Do not store them anywhere else.");
    }
}
