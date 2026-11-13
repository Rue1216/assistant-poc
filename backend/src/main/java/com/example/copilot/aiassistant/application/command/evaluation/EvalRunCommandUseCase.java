package com.example.copilot.aiassistant.application.command.evaluation;

import com.example.copilot.aiassistant.application.evaluation.EvalRunner;
import com.example.copilot.aiassistant.application.evaluation.StaleEvalRuns;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.eval.EvalDataset;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.RunningEvalDetail;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.config.ValidationError;
import com.example.copilot.aiassistant.domain.prompt.InstructionSelection;
import com.example.copilot.aiassistant.domain.prompt.PromptAssembler;
import com.example.copilot.aiassistant.application.config.PublishedConfigCache;
import com.example.copilot.aiassistant.domain.config.PublishedConfigs;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import com.example.shared.annotation.UseCase;

/**
 * Starts evaluation runs (prompt-evaluation spec 4.2). The synchronous checks and the snapshot happen
 * here; the cases are executed by {@link EvalRunner} on the single-thread evaluation executor.
 */
@UseCase
@Slf4j
public class EvalRunCommandUseCase {

    private static final String INTERNAL_ERROR = "AI_INTERNAL_ERROR";

    /** The two config sets of a run, frozen when it starts; each turn routes and assembles from its side. */
    public record EvalSnapshot(PublishedConfigs baseline, PublishedConfigs candidate) {
    }

    private final EvalRunRepository repository;
    private final AssistantConfigRepository configRepository;
    private final ConfigValidator validator;
    private final PublishedConfigCache cache;
    private final PromptAssembler assembler;
    private final EvalDataset dataset;
    private final EvalRunner runner;
    private final ExecutorService executor;
    private final Clock clock;
    private final StaleEvalRuns staleRuns;

    public EvalRunCommandUseCase(EvalRunRepository repository, AssistantConfigRepository configRepository,
                          ConfigValidator validator, PublishedConfigCache cache, PromptAssembler assembler,
                          EvalDataset dataset, EvalRunner runner, @Qualifier("evalExecutor") ExecutorService executor,
                          Clock clock, StaleEvalRuns staleRuns) {
        this.repository = repository;
        this.configRepository = configRepository;
        this.validator = validator;
        this.cache = cache;
        this.assembler = assembler;
        this.dataset = dataset;
        this.runner = runner;
        this.executor = executor;
        this.clock = clock;
        this.staleRuns = staleRuns;
    }

    public EvalRun start(ConfigKey configKey, long docVersion, String actor) {
        Instant now = clock.instant();
        staleRuns.markInterrupted(now);
        if (configRepository.findLatest(configKey).isEmpty()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND);
        }
        AssistantConfig workingCopy = configRepository.findWorkingCopy(configKey)
                .orElseThrow(() -> new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE));
        if (!Objects.equals(workingCopy.docVersion(), docVersion)) {
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        }
        List<ValidationError> errors = validator.validate(workingCopy.configType(), workingCopy.name(),
                workingCopy.changeNote(), workingCopy.content());
        if (!errors.isEmpty()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_VALIDATION_FAILED, List.copyOf(errors));
        }

        PublishedConfigs published = cache.loadFresh();
        EvalSnapshot snapshot = new EvalSnapshot(published, withCandidate(published, workingCopy));
        EvalRun draft = new EvalRun(null, configKey, workingCopy.version(), workingCopy.docVersion(),
                workingCopy.updatedAt(), assembler.assemble(snapshot.baseline(), InstructionSelection.all(), null).versions(),
                dataset.hash(), EvalStatus.RUNNING,
                null, actor, now, now, null, dataset.cases().size(), 0, null, List.of(), 0L);
        String id;
        try {
            id = repository.insert(draft);
        } catch (AssistantException e) {
            if (e.code() == ErrorCode.AI_EVAL_RUNNING) {
                throw runningConflict();
            }
            throw e;
        }
        EvalRun run = new EvalRun(id, draft.configKey(), draft.candidateVersion(), draft.candidateDocVersion(),
                draft.candidateUpdatedAt(), draft.baselineVersions(), draft.datasetHash(), draft.status(), null,
                draft.triggeredBy(), now, now, null, draft.totalCases(), 0, null, List.of(), 0L);
        try {
            executor.execute(() -> runner.run(run, snapshot));
        } catch (RejectedExecutionException e) {
            // Otherwise the RUNNING record would block every evaluation until it goes stale.
            repository.fail(id, 0L, INTERNAL_ERROR, clock.instant());
            log.error("eval run rejected runId={}", id);
            throw new AssistantException(ErrorCode.AI_INTERNAL_ERROR);
        }
        log.info("eval run started runId={} configKey={} cases={} triggeredBy={}", id, configKey,
                run.totalCases(), actor);
        return run;
    }

    /** Swaps the working copy into the published set (system prompt) or replaces / adds the instruction. */
    private static PublishedConfigs withCandidate(PublishedConfigs published, AssistantConfig workingCopy) {
        if (workingCopy.configType() == ConfigType.SYSTEM_PROMPT) {
            return new PublishedConfigs(workingCopy, published.instructions());
        }
        List<AssistantConfig> instructions = new ArrayList<>(published.instructions().stream()
                .filter(c -> !c.configKey().equals(workingCopy.configKey())).toList());
        instructions.add(workingCopy);
        return new PublishedConfigs(published.systemPrompt(), instructions);
    }

    private AssistantException runningConflict() {
        Optional<EvalRun> running = repository.findRunning();
        if (running.isEmpty()) {
            return new AssistantException(ErrorCode.AI_EVAL_RUNNING);
        }
        EvalRun r = running.get();
        return new AssistantException(ErrorCode.AI_EVAL_RUNNING, List.of(new RunningEvalDetail(
                r.id(), r.configKey().value(), r.triggeredBy(), r.completedCases(), r.totalCases())));
    }
}
