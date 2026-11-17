package dev.julco.kitchen.menu;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;

public enum Meal {
    BREAKFAST("breakfast"),
    LUNCH("lunch"),
    DINNER("dinner"),
    SNACK("snack");

    @EnumeratedValue
    private final String code;

    Meal(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** Parses a code checked beforehand (Bean Validation); an unknown code is a bug. */
    public static Meal fromCode(String code) {
        for (Meal meal : values()) {
            if (meal.code.equals(code)) {
                return meal;
            }
        }
        throw new IllegalArgumentException("Unknown meal code");
    }
}
