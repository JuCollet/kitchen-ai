package dev.julco.kitchen.menu;

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

    public String code() {
        return code;
    }
}
