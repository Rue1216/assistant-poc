package com.example.copilot.aiassistant.interfaces.rest.config.dto;

public final class ConfigKeys {

    public static final String PATTERN = "^(system|instruction)\\.[a-z0-9-]{1,50}$";

    /** A display name is one line: no control characters (including line breaks) or Unicode line separators. */
    public static final String NAME_PATTERN = "[^\\p{Cc}\\u2028\\u2029]*";

    private ConfigKeys() {
    }
}
