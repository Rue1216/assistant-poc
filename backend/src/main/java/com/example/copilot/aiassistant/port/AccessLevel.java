package com.example.copilot.aiassistant.port;

public enum AccessLevel {
    NONE, LIMITED, FULL;

    /** FULL covers LIMITED; NONE covers nothing. */
    public boolean covers(AccessLevel required) {
        if (this == NONE || required == NONE) {
            return false;
        }
        return this.ordinal() >= required.ordinal();
    }
}
