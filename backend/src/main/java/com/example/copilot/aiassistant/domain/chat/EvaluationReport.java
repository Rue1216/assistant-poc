package com.example.copilot.aiassistant.domain.chat;

import java.util.List;

/**
 * What one evaluation turn produced (prompt-evaluation spec 4.3): the redacted question, the shown answer without
 * citation markers, the tools the model asked for and what the turn cost. {@code answer} is null when the turn failed.
 * {@code routeSource} and {@code instructionKey} tell how the instruction was chosen; both are null when the turn
 * never reached routing, and the key is also null for "none" and for a fallback.
 */
public record EvaluationReport(String outcome, String errorCode, String redactedQuestion, String answer,
                               List<ToolCall> toolCalls, int modelCalls, long inputTokens, long outputTokens,
                               long totalTokens, long durationMs, List<String> unverifiedNumbers,
                               String routeSource, String instructionKey) {

    /** One requested tool call: its name and the {@code ToolStatus} name (executed or denied). */
    public record ToolCall(String toolName, String status) {
    }

    /** For a case that never reached the model: nothing was spent. */
    public static EvaluationReport failed(String errorCode) {
        return new EvaluationReport("FAILED", errorCode, null, null, List.of(), 0, 0, 0, 0, 0, List.of(), null, null);
    }
}
