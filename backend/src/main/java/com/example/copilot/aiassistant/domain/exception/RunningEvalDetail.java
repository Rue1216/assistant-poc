package com.example.copilot.aiassistant.domain.exception;

/** The evaluation run that is blocking a new start. */
public record RunningEvalDetail(String runId, String configKey, String triggeredBy, int completedCases,
                                int totalCases) implements ErrorDetail {
}
