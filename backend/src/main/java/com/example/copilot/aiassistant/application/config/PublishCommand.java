package com.example.copilot.aiassistant.application.config;

import java.time.Instant;

/** {@code scheduledAt} is required only for {@link PublishMode#SCHEDULED}. */
public record PublishCommand(PublishMode mode, Instant scheduledAt, long docVersion) {
}
