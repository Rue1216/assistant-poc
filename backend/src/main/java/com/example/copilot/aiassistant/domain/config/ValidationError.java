package com.example.copilot.aiassistant.domain.config;

/** One pre-publish validation failure; {@code message} is user-facing (Chinese). */
public record ValidationError(String rule, String message) {
}
