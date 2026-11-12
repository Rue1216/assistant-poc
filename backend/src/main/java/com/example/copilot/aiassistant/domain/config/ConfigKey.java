package com.example.copilot.aiassistant.domain.config;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import java.util.List;
import java.util.regex.Pattern;

/** Key of an assistant config document: system.main or instruction.<name>. */
public record ConfigKey(String value) {

    /** Accepted format. */
    public static final String PATTERN = "^(system|instruction)\\.[a-z0-9-]{1,50}$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    /** The system prompt key. */
    public static final ConfigKey SYSTEM_MAIN = new ConfigKey("system.main");

    public ConfigKey {
        if (value == null || !COMPILED.matcher(value).matches()) {
            throw new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(new FieldDetail("configKey")));
        }
    }

    public boolean isSystemPrompt() {
        return "system.main".equals(value);
    }

    public boolean isInstruction() {
        return value.startsWith("instruction.");
    }

    @Override
    public String toString() {
        return value;
    }
}
