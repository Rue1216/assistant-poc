package com.example.copilot.aiassistant.chat.dto;

import com.example.copilot.aiassistant.chat.ChatResult;
import com.example.copilot.aiassistant.config.dto.AdminTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 02 section 2.1 response; timestamps are ISO 8601 with a +08:00 offset. */
public record ChatResponseBody(String requestId, String answer, List<SourceBody> sources, OffsetDateTime queriedAt,
                               String disclaimer, List<ToolCallBody> toolCalls) {

    public record SourceBody(String name, String url, String dataTime, OffsetDateTime queriedAt) {
    }

    public record ToolCallBody(String name, String purpose, String status, long durationMs) {
    }

    public static ChatResponseBody from(ChatResult r) {
        return new ChatResponseBody(r.requestId(), r.answer(),
                r.sources().stream()
                        .map(s -> new SourceBody(s.name(), s.url(), s.dataTime(), AdminTime.at(s.queriedAt())))
                        .toList(),
                AdminTime.at(r.queriedAt()), r.disclaimer(),
                r.toolCalls().stream()
                        .map(t -> new ToolCallBody(t.name(), t.purpose(), t.status(), t.durationMs()))
                        .toList());
    }
}
