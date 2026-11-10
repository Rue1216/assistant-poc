package com.example.copilot.aiassistant.interfaces.rest.chat.dto;

import com.example.copilot.aiassistant.domain.chat.ChatResult;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.AdminTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 02 section 2.1 response with answer-citations spec 3.1; timestamps are ISO 8601 with a +08:00 offset. */
public record ChatResponseBody(String traceId, List<BlockBody> blocks, String answer, List<CitationBody> citations,
                               OffsetDateTime queriedAt, String disclaimer, List<ToolCallBody> toolCalls,
                               HistorySummaryBody historySummary) {

    public record HistorySummaryBody(String text, int summarizedCount) {
    }

    /** {@code type} is the lower-case block type; {@code citations} are citation ids (1, 2...). */
    public record BlockBody(String type, String text, List<Integer> citations) {
    }

    public record CitationBody(int id, String toolName, String title, String target, String excerpt,
                               String description, String sourceName, String url, String dataTime,
                               OffsetDateTime queriedAt, boolean cited) {
    }

    public record ToolCallBody(String name, String purpose, String status, long durationMs) {
    }

    public static ChatResponseBody from(ChatResult r) {
        return new ChatResponseBody(r.traceId(),
                r.blocks().stream().map(b -> new BlockBody(b.type().json(), b.text(), b.citations())).toList(),
                r.answer(),
                r.citations().stream()
                        .map(c -> new CitationBody(c.id(), c.toolName(), c.title(), c.target(), c.excerpt(),
                                c.description(), c.sourceName(), c.url(), c.dataTime(), AdminTime.at(c.queriedAt()),
                                c.cited()))
                        .toList(),
                AdminTime.at(r.queriedAt()), r.disclaimer(),
                r.toolCalls().stream()
                        .map(t -> new ToolCallBody(t.name(), t.purpose(), t.status(), t.durationMs()))
                        .toList(),
                r.historySummary() == null ? null
                        : new HistorySummaryBody(r.historySummary().text(), r.historySummary().summarizedCount()));
    }
}
