package com.example.copilot.aiassistant.domain.exception;

import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import java.time.Instant;

/** The working copy that an apply would overwrite. */
public record WorkingCopyDetail(ConfigStatus status, Instant scheduledAt, Instant updatedAt, long docVersion)
        implements ErrorDetail {
}
