package dev.julco.kitchen.user;

/**
 * A different Google account already uses this email. Accounts are never merged automatically:
 * that would let whoever controls the email take over the existing account.
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("Email already used by another account");
    }
}
