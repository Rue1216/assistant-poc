package com.example.copilot.aiassistant.application.query.evaluation;

import com.example.copilot.aiassistant.application.evaluation.StaleEvalRuns;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Reads evaluation runs (prompt-evaluation spec 4.2). Starting a run is {@code EvalRunCommandUseCase}. */
@Service
public class EvalRunQueryService {

    private static final int RECENT_LIMIT = 10;

    private final EvalRunRepository repository;
    private final Clock clock;
    private final StaleEvalRuns staleRuns;

    public EvalRunQueryService(EvalRunRepository repository, Clock clock, StaleEvalRuns staleRuns) {
        this.repository = repository;
        this.clock = clock;
        this.staleRuns = staleRuns;
    }

    /** The running evaluation, if any; a stale one is marked interrupted first. */
    public Optional<EvalRun> current() {
        staleRuns.markInterrupted(clock.instant());
        return repository.findRunning();
    }

    public EvalRun get(String runId) {
        return repository.findById(runId).orElseThrow(() -> new AssistantException(ErrorCode.AI_EVAL_NOT_FOUND));
    }

    /** The latest runs of the key, newest first, without their cases. */
    public List<EvalRun> recent(ConfigKey configKey) {
        return repository.recent(configKey, RECENT_LIMIT);
    }
}
