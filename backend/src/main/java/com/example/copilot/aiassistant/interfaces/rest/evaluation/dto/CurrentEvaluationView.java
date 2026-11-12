package com.example.copilot.aiassistant.interfaces.rest.evaluation.dto;

import com.example.copilot.aiassistant.interfaces.rest.config.dto.AdminTime;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import java.time.OffsetDateTime;

/** Progress of the running evaluation (prompt-evaluation spec 6.4). */
public record CurrentEvaluationView(String runId, String configKey, String triggeredBy, OffsetDateTime startedAt,
                                    int totalCases, int completedCases) {

    public static CurrentEvaluationView of(EvalRun r) {
        return new CurrentEvaluationView(r.id(), r.configKey().value(), r.triggeredBy(), AdminTime.at(r.startedAt()),
                r.totalCases(), r.completedCases());
    }
}
