package dev.julco.kitchen.household;

import jakarta.persistence.EnumeratedValue;

public enum MemberRole {
    OWNER("owner"),
    MEMBER("member");

    @EnumeratedValue
    private final String code;

    MemberRole(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
