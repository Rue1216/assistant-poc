package com.example.copilot.aiassistant.domain.eval;

import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import java.time.Instant;
import java.util.List;

/**
 * One evaluation run as stored in {@code COLL_ICRM_AI_ASSISTANT_EVAL_RUN} (prompt-evaluation spec 5.2).
 * {@code cases} is empty when the run was read by {@link EvalRunRepository#recent}.
 */
public record EvalRun(String id, ConfigKey configKey, int candidateVersion, long candidateDocVersion,
                      Instant candidateUpdatedAt, List<PromptVersion> baselineVersions, String datasetHash,
                      EvalStatus status, String errorCode, String triggeredBy, Instant startedAt,
                      Instant heartbeatAt, Instant finishedAt, int totalCases, int completedCases,
                      EvalSummary summary, List<EvalCaseRecord> cases, long docVersion) {

}
