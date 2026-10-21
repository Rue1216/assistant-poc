package com.example.copilot.aiassistant.chat;

import java.time.Instant;
import java.util.List;

/** The answer and its metadata (02 section 2.1 response). Holds no tool arguments or results. */
public record ChatResult(String requestId, String answer, List<SourceView> sources, Instant queriedAt,
                         String disclaimer, List<ToolCallSummary> toolCalls) {

    public ChatResult {
        sources = List.copyOf(sources);
        toolCalls = List.copyOf(toolCalls);
    }

    public record SourceView(String name, String url, String dataTime, Instant queriedAt) {
    }

    public record ToolCallSummary(String name, String purpose, String status, long durationMs) {
    }
}
