package com.example.copilot.aiassistant.domain.repository;

import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stores evaluation runs. Every update is conditional on {@code docVersion} and on the run still being RUNNING;
 * {@code false} means the run was changed (e.g. marked interrupted) in the meantime.
 */
public interface EvalRunRepository {

    /** Inserts the run and returns its id; a second RUNNING run raises AssistantException(AI_EVAL_RUNNING). */
    String insert(EvalRun run);

    Optional<EvalRun> findById(String id);

    Optional<EvalRun> findRunning();

    /** Newest first, without the {@code cases} array. */
    List<EvalRun> recent(ConfigKey configKey, int limit);

    /** Pushes one case result, counts it and refreshes the heartbeat. */
    boolean appendCase(String id, long docVersion, EvalCaseRecord c, Instant now);

    /** Heartbeat only: tells the stale check the run is alive and still allowed to continue. */
    boolean touch(String id, long docVersion, Instant now);

    boolean complete(String id, long docVersion, EvalSummary s, Instant now);

    boolean fail(String id, long docVersion, String errorCode, Instant now);
}
