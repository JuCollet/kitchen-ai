package dev.julco.kitchen.common;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;

/** Generic store aisles, valid in any store. Translated for display. */
public enum Aisle {
    PRODUCE("produce"),
    FROZEN("frozen"),
    MEAT_FISH("meat_fish"),
    DAIRY("dairy"),
    BAKERY("bakery"),
    DRY_GOODS("dry_goods"),
    CANNED("canned"),
    CONDIMENTS("condiments"),
    DRINKS("drinks"),
    HOUSEHOLD("household"),
    OTHER("other");

    @EnumeratedValue
    private final String code;

    Aisle(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** Parses a code checked beforehand (Bean Validation); an unknown code is a bug. */
    public static Aisle fromCode(String code) {
        for (Aisle aisle : values()) {
            if (aisle.code.equals(code)) {
                return aisle;
            }
        }
        throw new IllegalArgumentException("Unknown aisle code");
    }
}
