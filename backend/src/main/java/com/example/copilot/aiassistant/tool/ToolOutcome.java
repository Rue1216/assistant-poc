package com.example.copilot.aiassistant.tool;

/**
 * What the tool loop needs after one call. {@code result} holds the tool's unredacted data: never log it or
 * send it to the model; only {@code payloadForModel} (redacted, size-limited) goes to the model.
 */
public record ToolOutcome(String toolName, String purpose, ToolResult result, String payloadForModel,
                          long durationMs) {
}
