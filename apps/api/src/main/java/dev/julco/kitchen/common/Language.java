package dev.julco.kitchen.common;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;

/** Languages supported by Kitchen (site, instructions, PDF, widget). */
public enum Language {
    FR("fr"),
    NL("nl"),
    EN("en");

    /** Value stored in the database and in JSON. */
    @EnumeratedValue
    private final String code;

    Language(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }
}
