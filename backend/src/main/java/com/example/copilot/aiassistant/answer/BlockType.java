package com.example.copilot.aiassistant.answer;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Optional;

/** Block types of a structured answer; the JSON value is the lower-case name. */
public enum BlockType {
    HEADING, PARAGRAPH, BULLET, NUMBERED;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<BlockType> fromJson(String value) {
        for (BlockType type : values()) {
            if (type.json().equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
