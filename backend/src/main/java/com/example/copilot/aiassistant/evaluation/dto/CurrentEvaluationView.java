package com.example.copilot.aiassistant.evaluation.dto;

import com.example.copilot.aiassistant.config.dto.AdminTime;
import com.example.copilot.aiassistant.evaluation.EvalRun;
import java.time.OffsetDateTime;

/** Progress of the running evaluation (prompt-evaluation spec 6.4). */
public record CurrentEvaluationView(String runId, String configKey, String triggeredBy, OffsetDateTime startedAt,
                                    int totalCases, int completedCases) {

    public static CurrentEvaluationView of(EvalRun r) {
        return new CurrentEvaluationView(r.id(), r.configKey(), r.triggeredBy(), AdminTime.at(r.startedAt()),
                r.totalCases(), r.completedCases());
    }
}
