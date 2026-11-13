package com.example.copilot.aiassistant.interfaces.rest.config.dto;

import com.example.copilot.aiassistant.application.command.config.PublishMode;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotNull;

/** A malformed {@code scheduledAt} fails in Jackson and the exception handler reports it as that field. */
public record PublishRequest(
        @NotNull PublishMode publishMode,
        OffsetDateTime scheduledAt,
        @NotNull Long docVersion) {
}
