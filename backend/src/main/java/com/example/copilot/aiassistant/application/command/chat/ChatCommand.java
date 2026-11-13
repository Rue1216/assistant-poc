package com.example.copilot.aiassistant.application.command.chat;

import java.util.List;

/**
 * One question plus the history the front end sent back (raw text; the orchestrator redacts it). {@code categoryKey}
 * is the instruction a quick-question chip asked for, or null; it is only a hint and is checked against the published
 * instructions. {@code historySummary} is the summary of earlier turns the front end sent back, or null.
 */
public record ChatCommand(String message, List<HistoryMessage> history, String categoryKey, String historySummary) {

    public ChatCommand {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public ChatCommand(String message, List<HistoryMessage> history, String categoryKey) {
        this(message, history, categoryKey, null);
    }

    /** {@code role} is {@code user} or {@code assistant}. */
    public record HistoryMessage(String role, String content) {
    }
}
