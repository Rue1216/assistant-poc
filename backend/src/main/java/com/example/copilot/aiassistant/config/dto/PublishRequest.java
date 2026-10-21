package com.example.copilot.aiassistant.config.dto;

import com.example.copilot.aiassistant.config.PublishMode;
import jakarta.validation.constraints.NotNull;

/** {@code scheduledAt} stays a string so a missing or malformed value can be reported as that field. */
public record PublishRequest(
        @NotNull PublishMode publishMode,
        String scheduledAt,
        @NotNull Long docVersion) {
}
