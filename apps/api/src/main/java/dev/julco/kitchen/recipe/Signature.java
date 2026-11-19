package dev.julco.kitchen.recipe;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;

/** Whose name a publication shows: the household's, or the person's who published it. */
public enum Signature {
    HOUSEHOLD("household"),
    PERSON("person");

    @EnumeratedValue
    private final String code;

    Signature(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }
}
