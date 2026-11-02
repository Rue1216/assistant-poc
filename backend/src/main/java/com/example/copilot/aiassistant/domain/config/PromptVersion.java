package com.example.copilot.aiassistant.domain.config;

/** The version of one published config that went into an assembled prompt. */
public record PromptVersion(String configKey, int version) {
}
