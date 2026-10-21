package com.example.copilot.aiassistant.config;

/** One pre-publish validation failure; {@code message} is user-facing (Chinese). */
public record ValidationError(String rule, String message) {
}
