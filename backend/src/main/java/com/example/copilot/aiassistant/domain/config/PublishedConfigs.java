package com.example.copilot.aiassistant.domain.config;

import java.util.List;

/** The currently published system prompt (may be absent) and instruction configs. */
public record PublishedConfigs(AssistantConfig systemPrompt, List<AssistantConfig> instructions) {
}
