package com.example.copilot.aiassistant.domain.tool;

/**
 * What a tool says about its own result for the citation list. External tools fill {@code target} and
 * {@code excerpt}; internal tools only {@code description}. Never put customer identifiers or tool arguments here.
 */
public record CitationDetail(String title, String target, String excerpt, String description, Integer resultCount) {
}
