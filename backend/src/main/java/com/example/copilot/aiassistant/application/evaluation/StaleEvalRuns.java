package com.example.copilot.aiassistant.application.evaluation;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.domain.eval.EvalErrors;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Marks a RUNNING evaluation whose heartbeat is older than the stale threshold as interrupted. */
@Component
public class StaleEvalRuns {

    private final EvalRunRepository repository;
    private final AiAssistantProperties.Eval properties;

    public StaleEvalRuns(EvalRunRepository repository, AiAssistantProperties properties) {
        this.repository = repository;
        this.properties = properties.eval();
    }

    public void markInterrupted(Instant now) {
        Instant threshold = now.minus(properties.staleAfter());
        repository.findRunning().filter(r -> r.heartbeatAt().isBefore(threshold))
                .ifPresent(r -> repository.fail(r.id(), r.docVersion(), EvalErrors.INTERRUPTED, now));
    }
}
