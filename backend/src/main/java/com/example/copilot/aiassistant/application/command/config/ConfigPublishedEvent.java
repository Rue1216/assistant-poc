package com.example.copilot.aiassistant.application.command.config;

import com.example.copilot.aiassistant.domain.config.ConfigKey;

/** A version of {@code configKey} went live; published inside the transaction, acted on after it commits. */
public record ConfigPublishedEvent(ConfigKey configKey) {
}
