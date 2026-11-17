package dev.julco.kitchen.common;

/**
 * Input that is well formed but breaks a business rule. The message is written in English for the
 * AI, so that it can correct itself ("mode names must be unique"); it never contains personal data
 * or secrets. The site API only sends a stable code.
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
