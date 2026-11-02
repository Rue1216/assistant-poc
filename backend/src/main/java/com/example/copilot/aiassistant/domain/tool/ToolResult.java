package com.example.copilot.aiassistant.domain.tool;

import java.util.List;

public record ToolResult(ToolStatus status, String errorCode, String message, Object data, List<Source> sources,
                         CitationDetail citation) {

    public ToolResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public static ToolResult success(Object data, List<Source> sources, CitationDetail citation) {
        return new ToolResult(ToolStatus.SUCCESS, null, null, data, sources, citation);
    }

    /** Without citation detail; for fake tools in tests. */
    public static ToolResult success(Object data, List<Source> sources) {
        return success(data, sources, null);
    }

    public static ToolResult failure(ToolStatus status, String errorCode, String message) {
        return new ToolResult(status, errorCode, message, null, List.of(), null);
    }
}
