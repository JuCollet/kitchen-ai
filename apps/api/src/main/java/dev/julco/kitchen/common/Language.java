package dev.julco.kitchen.common;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;
import java.util.Locale;

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

    /** Parses a code sent by a client ("fr", "NL "…). */
    public static Language fromCode(String code) {
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (language.code.equals(normalized)) {
                return language;
            }
        }
        throw new InvalidInputException("language must be fr, nl or en.");
    }
}
