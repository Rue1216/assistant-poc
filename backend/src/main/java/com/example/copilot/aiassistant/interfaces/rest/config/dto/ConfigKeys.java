package com.example.copilot.aiassistant.interfaces.rest.config.dto;

public final class ConfigKeys {

    /** A display name is one line: no control characters (including line breaks) or Unicode line separators. */
    public static final String NAME_PATTERN = "[^\\p{Cc}\\u2028\\u2029]*";

    private ConfigKeys() {
    }
}
