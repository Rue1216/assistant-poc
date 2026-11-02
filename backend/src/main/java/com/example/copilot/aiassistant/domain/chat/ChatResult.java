package com.example.copilot.aiassistant.domain.chat;

import com.example.copilot.aiassistant.domain.answer.AnswerBlock;
import java.time.Instant;
import java.util.List;

/**
 * The answer and its metadata (02 section 2.1 response, answer-citations spec 3.1). Holds no tool arguments or
 * results; citation texts are already redacted.
 */
public record ChatResult(String requestId, List<AnswerBlock> blocks, String answer, List<CitationView> citations,
                         Instant queriedAt, String disclaimer, List<ToolCallSummary> toolCalls,
                         HistorySummary historySummary) {

    public ChatResult {
        blocks = List.copyOf(blocks);
        citations = List.copyOf(citations);
        toolCalls = List.copyOf(toolCalls);
    }

    /** One successful tool call; {@code cited} is false when no block refers to it. */
    public record CitationView(int id, String toolName, String title, String target, String excerpt,
                               String description, String sourceName, String url, String dataTime,
                               Instant queriedAt, boolean cited) {
    }

    /** The summary made this turn and how many history messages it folded in; the result's field is null if none. */
    public record HistorySummary(String text, int summarizedCount) {
    }

    public record ToolCallSummary(String name, String purpose, String status, long durationMs) {
    }
}
