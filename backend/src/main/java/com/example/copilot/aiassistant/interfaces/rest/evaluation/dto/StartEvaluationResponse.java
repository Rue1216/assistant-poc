package com.example.copilot.aiassistant.interfaces.rest.evaluation.dto;

import com.example.copilot.aiassistant.domain.eval.EvalRun;

public record StartEvaluationResponse(String runId, String status, int totalCases) {

    public static StartEvaluationResponse of(EvalRun run) {
        return new StartEvaluationResponse(run.id(), run.status().name(), run.totalCases());
    }
}
