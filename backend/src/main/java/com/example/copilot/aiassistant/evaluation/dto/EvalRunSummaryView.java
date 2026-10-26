package com.example.copilot.aiassistant.evaluation.dto;

import com.example.copilot.aiassistant.config.dto.AdminTime;
import com.example.copilot.aiassistant.evaluation.EvalRun;
import com.example.copilot.aiassistant.evaluation.EvalSummary;
import java.time.OffsetDateTime;

/** One row of the recent-runs list (prompt-evaluation spec 6.3); no cases, snapshot or internal counters. */
public record EvalRunSummaryView(String runId, String status, String errorCode, String triggeredBy,
                                 OffsetDateTime startedAt, OffsetDateTime finishedAt, int candidateVersion,
                                 OffsetDateTime candidateUpdatedAt, long candidateDocVersion, boolean datasetChanged,
                                 int totalCases, int completedCases, EvalSummary summary) {

    public static EvalRunSummaryView of(EvalRun r, String currentDatasetHash) {
        return new EvalRunSummaryView(r.id(), r.status().name(), r.errorCode(), r.triggeredBy(),
                AdminTime.at(r.startedAt()), AdminTime.at(r.finishedAt()), r.candidateVersion(),
                AdminTime.at(r.candidateUpdatedAt()), r.candidateDocVersion(),
                !r.datasetHash().equals(currentDatasetHash), r.totalCases(), r.completedCases(), r.summary());
    }
}
