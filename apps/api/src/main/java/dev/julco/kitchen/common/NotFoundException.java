package dev.julco.kitchen.common;

/**
 * A resource that does not exist or belongs to another household. Both cases are reported the
 * same way (404), so that nobody can probe for other households' data (CLAUDE.md rule 2).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Not found");
    }
}
