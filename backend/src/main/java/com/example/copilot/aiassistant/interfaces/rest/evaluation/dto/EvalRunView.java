package com.example.copilot.aiassistant.interfaces.rest.evaluation.dto;

import com.example.copilot.aiassistant.interfaces.rest.config.dto.AdminTime;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalSummary;
import java.time.OffsetDateTime;
import java.util.List;

/** One run with all its case results (prompt-evaluation spec 6.5). */
public record EvalRunView(String runId, String configKey, String status, String errorCode, String triggeredBy,
                          OffsetDateTime startedAt, OffsetDateTime finishedAt, int candidateVersion,
                          OffsetDateTime candidateUpdatedAt, long candidateDocVersion, boolean datasetChanged,
                          int totalCases, int completedCases, EvalSummary summary, List<EvalCaseRecord> cases) {

    public static EvalRunView of(EvalRun r, String currentDatasetHash) {
        return new EvalRunView(r.id(), r.configKey(), r.status().name(), r.errorCode(), r.triggeredBy(),
                AdminTime.at(r.startedAt()), AdminTime.at(r.finishedAt()), r.candidateVersion(),
                AdminTime.at(r.candidateUpdatedAt()), r.candidateDocVersion(),
                !r.datasetHash().equals(currentDatasetHash), r.totalCases(), r.completedCases(), r.summary(),
                r.cases());
    }
}
