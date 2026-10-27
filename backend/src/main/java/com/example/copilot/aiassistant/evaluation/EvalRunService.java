package com.example.copilot.aiassistant.evaluation;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.common.AssistantException;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.config.AssistantConfig;
import com.example.copilot.aiassistant.config.AssistantConfigRepository;
import com.example.copilot.aiassistant.config.ConfigService;
import com.example.copilot.aiassistant.config.ConfigType;
import com.example.copilot.aiassistant.config.ConfigValidator;
import com.example.copilot.aiassistant.config.ValidationError;
import com.example.copilot.aiassistant.prompt.InstructionSelection;
import com.example.copilot.aiassistant.prompt.PromptAssembler;
import com.example.copilot.aiassistant.prompt.PublishedConfigCache;
import com.example.copilot.aiassistant.prompt.PublishedConfigCache.PublishedConfigs;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Starts and reads evaluation runs (prompt-evaluation spec 4.2). The synchronous checks and the snapshot happen
 * here; the cases are executed by {@link EvalRunner} on the single-thread evaluation executor.
 */
@Service
public class EvalRunService {

    private static final Logger log = LoggerFactory.getLogger(EvalRunService.class);
    private static final int RECENT_LIMIT = 10;
    private static final String INTERNAL_ERROR = "AI_INTERNAL_ERROR";

    /** The two config sets of a run, frozen when it starts; each turn routes and assembles from its side. */
    public record EvalSnapshot(PublishedConfigs baseline, PublishedConfigs candidate) {
    }

    private final EvalRunRepository repository;
    private final ConfigService configService;
    private final AssistantConfigRepository configRepository;
    private final ConfigValidator validator;
    private final PublishedConfigCache cache;
    private final PromptAssembler assembler;
    private final EvalDataset dataset;
    private final EvalRunner runner;
    private final ExecutorService executor;
    private final Clock clock;
    private final AiAssistantProperties.Eval properties;

    public EvalRunService(EvalRunRepository repository, ConfigService configService,
                          AssistantConfigRepository configRepository, ConfigValidator validator,
                          PublishedConfigCache cache, PromptAssembler assembler, EvalDataset dataset,
                          EvalRunner runner, @Qualifier("evalExecutor") ExecutorService executor, Clock clock,
                          AiAssistantProperties properties) {
        this.repository = repository;
        this.configService = configService;
        this.configRepository = configRepository;
        this.validator = validator;
        this.cache = cache;
        this.assembler = assembler;
        this.dataset = dataset;
        this.runner = runner;
        this.executor = executor;
        this.clock = clock;
        this.properties = properties.eval();
    }

    public EvalRun start(String configKey, long docVersion, String actor) {
        Instant now = clock.instant();
        markStaleInterrupted(now);
        if (configRepository.findTopByConfigKeyOrderByVersionDesc(configKey).isEmpty()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND);
        }
        AssistantConfig workingCopy = configService.findWorkingCopy(configKey)
                .orElseThrow(() -> new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE));
        if (!Objects.equals(workingCopy.getDocVersion(), docVersion)) {
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        }
        List<ValidationError> errors = validator.validate(workingCopy.getConfigType(), workingCopy.getName(),
                workingCopy.getContent());
        if (!errors.isEmpty()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_VALIDATION_FAILED, List.copyOf(errors));
        }

        PublishedConfigs published = cache.loadFresh();
        EvalSnapshot snapshot = new EvalSnapshot(published, withCandidate(published, workingCopy));
        EvalRun draft = new EvalRun(null, configKey, workingCopy.getVersion(), workingCopy.getDocVersion(),
                workingCopy.getUpdatedAt(), assembler.assemble(snapshot.baseline(), InstructionSelection.all()).versions(),
                dataset.hash(), EvalStatus.RUNNING,
                null, actor, now, now, null, dataset.cases().size(), 0, null, List.of(), 0L);
        String id;
        try {
            id = repository.insert(draft);
        } catch (DuplicateKeyException e) {
            throw runningConflict();
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

    /** The running evaluation, if any; a stale one is marked interrupted first. */
    public Optional<EvalRun> current() {
        markStaleInterrupted(clock.instant());
        return repository.findRunning();
    }

    public EvalRun get(String runId) {
        return repository.findById(runId).orElseThrow(() -> new AssistantException(ErrorCode.AI_EVAL_NOT_FOUND));
    }

    /** The latest runs of the key, newest first, without their cases. */
    public List<EvalRun> recent(String configKey) {
        return repository.recent(configKey, RECENT_LIMIT);
    }

    /** Swaps the working copy into the published set (system prompt) or replaces / adds the instruction. */
    private static PublishedConfigs withCandidate(PublishedConfigs published, AssistantConfig workingCopy) {
        if (workingCopy.getConfigType() == ConfigType.SYSTEM_PROMPT) {
            return new PublishedConfigs(workingCopy, published.instructions());
        }
        List<AssistantConfig> instructions = new ArrayList<>(published.instructions().stream()
                .filter(c -> !c.getConfigKey().equals(workingCopy.getConfigKey())).toList());
        instructions.add(workingCopy);
        return new PublishedConfigs(published.systemPrompt(), instructions);
    }

    private void markStaleInterrupted(Instant now) {
        Instant threshold = now.minus(properties.staleAfter());
        repository.findRunning().filter(r -> r.heartbeatAt().isBefore(threshold))
                .ifPresent(r -> repository.fail(r.id(), r.docVersion(), EvalErrors.INTERRUPTED, now));
    }

    private AssistantException runningConflict() {
        Optional<EvalRun> running = repository.findRunning();
        if (running.isEmpty()) {
            return new AssistantException(ErrorCode.AI_EVAL_RUNNING);
        }
        EvalRun r = running.get();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("runId", r.id());
        details.put("configKey", r.configKey());
        details.put("triggeredBy", r.triggeredBy());
        details.put("completedCases", r.completedCases());
        details.put("totalCases", r.totalCases());
        return new AssistantException(ErrorCode.AI_EVAL_RUNNING, List.of(details));
    }
}
