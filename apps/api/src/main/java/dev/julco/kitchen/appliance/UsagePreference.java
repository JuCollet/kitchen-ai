package dev.julco.kitchen.appliance;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;

/** How the AI may use an appliance (docs/SCOPE.md, "Appliances"). */
public enum UsagePreference {
    /** Every recipe uses it whenever possible. */
    DEFAULT("default"),
    /** The AI may suggest it when relevant. */
    SUGGEST("suggest"),
    /** Only when explicitly asked. */
    ON_REQUEST("on_request");

    @EnumeratedValue
    private final String code;

    UsagePreference(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }
}
