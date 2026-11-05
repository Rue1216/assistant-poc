package com.example.copilot.aiassistant.domain.tool;

/** One choice a workflow marker may name after the tool, e.g. a customer-360 section. */
public record ToolOption(String key, String name, String description) {
}
