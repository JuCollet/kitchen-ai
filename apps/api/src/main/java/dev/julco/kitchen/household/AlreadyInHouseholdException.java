package dev.julco.kitchen.household;

/** A person belongs to a single household in v1. */
public class AlreadyInHouseholdException extends RuntimeException {

    public AlreadyInHouseholdException() {
        super("This person already belongs to a household");
    }
}
