package dev.julco.kitchen.pantry;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;

/** Where a pantry item comes from: a checked shopping list item, or a manual update. */
public enum PantrySource {
    SHOPPING("shopping"),
    MANUAL("manual");

    @EnumeratedValue
    private final String code;

    PantrySource(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }
}
