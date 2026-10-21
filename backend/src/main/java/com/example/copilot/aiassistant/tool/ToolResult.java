package com.example.copilot.aiassistant.tool;

import java.util.List;

public record ToolResult(ToolStatus status, String errorCode, String message, Object data, List<Source> sources) {

    public ToolResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public static ToolResult success(Object data, List<Source> sources) {
        return new ToolResult(ToolStatus.SUCCESS, null, null, data, sources);
    }

    public static ToolResult failure(ToolStatus status, String errorCode, String message) {
        return new ToolResult(status, errorCode, message, null, List.of());
    }
}
