package com.example.copilot.aiassistant.prompt;

import java.util.List;

/** The assembled system message and the config versions it was built from (sorted by configKey, immutable). */
public record AssembledPrompt(String text, List<PromptVersion> versions) {

    public AssembledPrompt {
        versions = List.copyOf(versions);
    }
}
