package com.example.copilot.aiassistant.application.command.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.example.copilot.aiassistant.application.evaluation.StaleEvalRuns;
import com.example.copilot.aiassistant.application.evaluation.EvalRunner;
import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.domain.chat.EvaluationReport;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.eval.CaseResult;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalDataset;
import com.example.copilot.aiassistant.domain.eval.EvalErrors;
import com.example.copilot.aiassistant.domain.eval.EvalMetrics;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import com.example.copilot.aiassistant.domain.eval.EvalSummary;
import com.example.copilot.aiassistant.domain.eval.JudgeVerdict;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.RunningEvalDetail;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.infrastructure.mongo.AiAssistantCollections;
import com.example.copilot.aiassistant.application.command.config.ConfigCommandUseCase;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.PublishedConfigs;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.application.command.config.SaveCommand;
import com.example.copilot.aiassistant.domain.config.SaveMode;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.example.copilot.aiassistant.domain.config.ValidationError;
import com.example.copilot.aiassistant.domain.prompt.InstructionSelection;
import com.example.copilot.aiassistant.domain.prompt.PromptAssembler;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import com.example.copilot.aiassistant.application.config.PublishedConfigCache;
import com.example.copilot.aiassistant.application.query.evaluation.EvalRunQueryService;
import com.example.copilot.testsupport.LogCapture;
import com.example.copilot.testsupport.ConfigDocumentStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class EvalRunCommandUseCaseTest extends IntegrationTestBase {

    private static final ConfigKey KEY = new ConfigKey("instruction.contact-prep");
    private static final String ACTOR = "EEE22222";

    @MockitoBean EvalRunner runner;

    @Autowired EvalRunCommandUseCase service;
    @Autowired EvalRunQueryService queries;
    @Autowired EvalRunRepository runs;
    @Autowired ConfigCommandUseCase configService;
    @Autowired AssistantConfigRepository configs;
    ConfigDocumentStore configDocuments;

    @Autowired
    void initConfigDocuments(MongoTemplate template) {
        configDocuments = new ConfigDocumentStore(template);
    }
    @Autowired SeedLoader seedLoader;
    @Autowired EvalDataset dataset;
    @Autowired MongoTemplate mongo;
    @Autowired Clock clock;
    @Autowired ConfigValidator validator;
    @Autowired PublishedConfigCache cache;
    @Autowired PromptAssembler assembler;
    @Autowired AiAssistantProperties properties;

    @BeforeEach
    void setUp() {
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), AiAssistantCollections.EVAL_RUN);
        reset(runner);
    }

    @AfterEach
    void tearDown() {
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), AiAssistantCollections.EVAL_RUN);
        configDocuments.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    private AssistantConfig draftWithMarker(ConfigKey key, String marker) {
        AssistantConfig draft = configService.createDraft(key, ACTOR);
        return configService.saveWorkingCopy(key, new SaveCommand(draft.name(),
                draft.content() + "\n- " + marker + "\n", "note", SaveMode.KEEP_STATUS, draft.docVersion()),
                ACTOR);
    }

    private EvalRunCommandUseCase.EvalSnapshot capturedSnapshot() {
        ArgumentCaptor<EvalRunCommandUseCase.EvalSnapshot> captor =
                ArgumentCaptor.forClass(EvalRunCommandUseCase.EvalSnapshot.class);
        verify(runner, timeout(5000)).run(any(), captor.capture());
        return captor.getValue();
    }

    private String textOf(PublishedConfigs configs) {
        return assembler.assemble(configs, InstructionSelection.all(), null).text();
    }

    private List<PromptVersion> versionsOf(PublishedConfigs configs) {
        return assembler.assemble(configs, InstructionSelection.all(), null).versions();
    }

    private EvalRun running(Instant heartbeat) {
        Instant now = clock.instant();
        return new EvalRun(null, KEY, 2, 0L, now, List.of(new PromptVersion(KEY.value(), 1)), "h".repeat(64),
                EvalStatus.RUNNING, null, "someone", now.minus(Duration.ofMinutes(30)), heartbeat, null, 16, 3, null,
                List.of(), 0L);
    }

    private static AssistantException thrown(Runnable r) {
        try {
            r.run();
        } catch (AssistantException e) {
            return e;
        }
        throw new AssertionError("expected AssistantException");
    }

    @Test
    void startCreatesRunningRunWithSnapshot() {
        AssistantConfig draft = draftWithMarker(KEY, "marker-draft-text");
        EvalRun run = service.start(KEY, draft.docVersion(), ACTOR);

        assertThat(run.id()).isNotBlank();
        assertThat(run.status()).isEqualTo(EvalStatus.RUNNING);
        assertThat(run.totalCases()).isEqualTo(dataset.cases().size());
        assertThat(run.completedCases()).isZero();
        assertThat(run.triggeredBy()).isEqualTo(ACTOR);
        assertThat(run.candidateVersion()).isEqualTo(draft.version());
        assertThat(run.candidateDocVersion()).isEqualTo(draft.docVersion());
        assertThat(run.candidateUpdatedAt().toEpochMilli()).isEqualTo(draft.updatedAt().toEpochMilli());
        assertThat(run.baselineVersions()).hasSize(8);
        assertThat(run.datasetHash()).isEqualTo(dataset.hash());

        EvalRunCommandUseCase.EvalSnapshot snapshot = capturedSnapshot();
        assertThat(textOf(snapshot.candidate())).contains("marker-draft-text");
        assertThat(textOf(snapshot.baseline())).doesNotContain("marker-draft-text");
        EvalRun stored = queries.get(run.id());
        assertThat(stored.status()).isEqualTo(EvalStatus.RUNNING);
        assertThat(stored.baselineVersions()).isEqualTo(run.baselineVersions());
    }

    @Test
    void snapshotIgnoresLaterEdits() {
        AssistantConfig draft = draftWithMarker(KEY, "marker-old");
        service.start(KEY, draft.docVersion(), ACTOR);
        configService.saveWorkingCopy(KEY, new SaveCommand(draft.name(),
                draft.content() + "\n- marker-new\n", "note", SaveMode.KEEP_STATUS, draft.docVersion()),
                ACTOR);

        EvalRunCommandUseCase.EvalSnapshot snapshot = capturedSnapshot();
        assertThat(textOf(snapshot.candidate())).contains("marker-old").doesNotContain("marker-new");
    }

    @Test
    void newKeyIsAddedToCandidate() {
        ConfigKey key = new ConfigKey("instruction.eval-new");
        AssistantConfig base = configs.findPublished(KEY).orElseThrow();
        AssistantConfig created = configService.createKey(key, ConfigType.SYSTEM_INSTRUCTION, "新分類", ACTOR);
        AssistantConfig saved = configService.saveWorkingCopy(key, new SaveCommand("新分類", base.content(),
                "note", SaveMode.KEEP_STATUS, created.docVersion()), ACTOR);

        EvalRun run = service.start(key, saved.docVersion(), ACTOR);

        EvalRunCommandUseCase.EvalSnapshot snapshot = capturedSnapshot();
        assertThat(versionsOf(snapshot.baseline())).hasSize(8).noneMatch(v -> v.configKey().equals(key.value()));
        assertThat(versionsOf(snapshot.candidate())).hasSize(9).anyMatch(v -> v.configKey().equals(key.value()));
        assertThat(run.baselineVersions()).hasSize(8);
    }

    @Test
    void systemMainWorkingCopyReplacesSystemPrompt() {
        AssistantConfig draft = draftWithMarker(new ConfigKey("system.main"), "marker-main-draft");
        service.start(new ConfigKey("system.main"), draft.docVersion(), ACTOR);

        EvalRunCommandUseCase.EvalSnapshot snapshot = capturedSnapshot();
        assertThat(textOf(snapshot.candidate())).contains("marker-main-draft");
        assertThat(textOf(snapshot.baseline())).doesNotContain("marker-main-draft");
        assertThat(versionsOf(snapshot.candidate())).hasSize(8);
        assertThat(versionsOf(snapshot.candidate())).anyMatch(v -> v.configKey().equals("system.main")
                && v.version() == draft.version());
    }

    @Test
    void unknownKeyIsNotFound() {
        assertThat(thrown(() -> service.start(new ConfigKey("instruction.nope"), 0L, ACTOR)).code())
                .isEqualTo(ErrorCode.AI_CONFIG_NOT_FOUND);
    }

    @Test
    void noWorkingCopyIsInvalidState() {
        assertThat(thrown(() -> service.start(KEY, 0L, ACTOR)).code())
                .isEqualTo(ErrorCode.AI_CONFIG_INVALID_STATE);
    }

    @Test
    void staleDocVersionIsConflict() {
        AssistantConfig draft = draftWithMarker(KEY, "m");
        assertThat(thrown(() -> service.start(KEY, draft.docVersion() + 1, ACTOR)).code())
                .isEqualTo(ErrorCode.AI_CONFIG_CONFLICT);
    }

    @Test
    void forbiddenContentFailsValidation() {
        AssistantConfig draft = draftWithMarker(KEY, "key sk-abcdefghijklmnopqrstuvwxyz0123456789");
        AssistantException e = thrown(() -> service.start(KEY, draft.docVersion(), ACTOR));
        assertThat(e.code()).isEqualTo(ErrorCode.AI_CONFIG_VALIDATION_FAILED);
        assertThat(e.details()).anyMatch(d -> d instanceof ValidationError v && v.rule().equals("FORBIDDEN_CONTENT"));
        verify(runner, never()).run(any(), any());
    }

    @Test
    void forbiddenChangeNoteFailsValidation() {
        AssistantConfig draft = configService.createDraft(KEY, ACTOR);
        AssistantConfig stale = configs.save(draft.withContent(draft.name(), draft.content(), "0912-345-678"));

        AssistantException e = thrown(() -> service.start(KEY, stale.docVersion(), ACTOR));

        assertThat(e.code()).isEqualTo(ErrorCode.AI_CONFIG_VALIDATION_FAILED);
        assertThat(e.details()).anyMatch(d -> d instanceof ValidationError v && v.rule().equals("FORBIDDEN_CONTENT"));
        verify(runner, never()).run(any(), any());
    }

    @Test
    void secondStartIsRejectedWhileRunning() {
        AssistantConfig draft = draftWithMarker(KEY, "m");
        EvalRun first = service.start(KEY, draft.docVersion(), ACTOR);

        AssistantException e = thrown(() -> service.start(KEY, draft.docVersion(), "other.admin"));

        assertThat(e.code()).isEqualTo(ErrorCode.AI_EVAL_RUNNING);
        assertThat(e.details()).isEqualTo(
                List.of(new RunningEvalDetail(first.id(), KEY.value(), ACTOR, 0, dataset.cases().size())));
    }

    @Test
    void concurrentStartsYieldOneRun() throws Exception {
        AssistantConfig draft = draftWithMarker(KEY, "m");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String actor = "admin" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return service.start(KEY, draft.docVersion(), actor);
                    } catch (AssistantException e) {
                        return e;
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get());
            }
            assertThat(results.stream().filter(EvalRun.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(AssistantException.class::isInstance)
                    .map(o -> ((AssistantException) o).code())).containsExactly(ErrorCode.AI_EVAL_RUNNING);
            assertThat(mongo.getCollection(AiAssistantCollections.EVAL_RUN)
                    .countDocuments(new org.bson.Document("status", "RUNNING"))).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectedSubmitFailsRunAndFreesTheSlot() {
        ExecutorService rejecting = Executors.newSingleThreadExecutor();
        rejecting.shutdown();
        EvalRunCommandUseCase rejectingService = new EvalRunCommandUseCase(runs, configs, validator, cache,
                assembler, dataset, runner, rejecting, clock, new StaleEvalRuns(runs, properties));
        AssistantConfig draft = draftWithMarker(KEY, "m");
        List<LogEvent> events;
        AssistantException e;
        try (LogCapture capture = LogCapture.of(EvalRunCommandUseCase.class, Level.INFO)) {
            e = thrown(() -> rejectingService.start(KEY, draft.docVersion(), ACTOR));
            events = capture.events();
        }

        assertThat(e.code()).isEqualTo(ErrorCode.AI_INTERNAL_ERROR);
        List<EvalRun> stored = runs.recent(KEY, 10);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).status()).isEqualTo(EvalStatus.FAILED);
        assertThat(stored.get(0).errorCode()).isEqualTo("AI_INTERNAL_ERROR");
        assertThat(stored.get(0).finishedAt()).isNotNull();
        List<String> lines = events.stream().map(LogCapture::render).toList();
        assertThat(lines).containsExactly("eval run rejected runId=" + stored.get(0).id());
        verify(runner, never()).run(any(), any());
        // The slot is free again: the next start is accepted.
        assertThat(service.start(KEY, draft.docVersion(), ACTOR).status()).isEqualTo(EvalStatus.RUNNING);
    }

    @Test
    void staleRunIsMarkedInterrupted() {
        String stale = runs.insert(running(clock.instant().minus(Duration.ofMinutes(11))));

        assertThat(queries.current()).isEmpty();
        EvalRun after = queries.get(stale);
        assertThat(after.status()).isEqualTo(EvalStatus.FAILED);
        assertThat(after.errorCode()).isEqualTo(EvalErrors.INTERRUPTED);
        assertThat(after.finishedAt()).isNotNull();

        AssistantConfig draft = draftWithMarker(KEY, "m");
        assertThat(service.start(KEY, draft.docVersion(), ACTOR).status()).isEqualTo(EvalStatus.RUNNING);
    }

}
