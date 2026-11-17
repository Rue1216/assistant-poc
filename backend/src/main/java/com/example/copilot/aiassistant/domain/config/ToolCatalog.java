package com.example.copilot.aiassistant.domain.config;

import java.util.List;
import java.util.Optional;

/**
 * What config validation needs to know about the tools: which names exist and which options each one accepts.
 * Kept this narrow so the validator can be shared with a module that has no tools.
 */
public interface ToolCatalog {

    /** The option keys a marker may name after the tool (empty list: none); empty when no such tool exists. */
    Optional<List<String>> optionKeys(String toolName);
}
