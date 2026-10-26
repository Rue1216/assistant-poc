package com.example.copilot.aiassistant.evaluation.dto;

import com.example.copilot.aiassistant.evaluation.EvalRun;

public record StartEvaluationResponse(String runId, String status, int totalCases) {

    public static StartEvaluationResponse of(EvalRun run) {
        return new StartEvaluationResponse(run.id(), run.status().name(), run.totalCases());
    }
}
