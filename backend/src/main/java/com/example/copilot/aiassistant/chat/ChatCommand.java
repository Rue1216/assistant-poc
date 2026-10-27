package com.example.copilot.aiassistant.chat;

import java.util.List;

/**
 * One question plus the history the front end sent back (raw text; the orchestrator redacts it). {@code categoryKey}
 * is the instruction a quick-question chip asked for, or null; it is only a hint and is checked against the published
 * instructions.
 */
public record ChatCommand(String message, List<HistoryMessage> history, String categoryKey) {

    public ChatCommand {
        history = history == null ? List.of() : List.copyOf(history);
    }

    /** {@code role} is {@code user} or {@code assistant}. */
    public record HistoryMessage(String role, String content) {
    }
}
