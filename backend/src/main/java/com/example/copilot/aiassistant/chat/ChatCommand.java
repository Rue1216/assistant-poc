package com.example.copilot.aiassistant.chat;

import java.util.List;

/** One question plus the history the front end sent back (raw text; the orchestrator redacts it). */
public record ChatCommand(String message, List<HistoryMessage> history) {

    public ChatCommand {
        history = history == null ? List.of() : List.copyOf(history);
    }

    /** {@code role} is {@code user} or {@code assistant}. */
    public record HistoryMessage(String role, String content) {
    }
}
