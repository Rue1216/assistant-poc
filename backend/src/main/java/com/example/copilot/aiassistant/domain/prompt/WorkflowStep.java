package com.example.copilot.aiassistant.domain.prompt;

import java.util.List;

/** One required tool call from an instruction's {{tool: options}} markers, with the merged options. */
public record WorkflowStep(String toolName, List<String> options) {}
