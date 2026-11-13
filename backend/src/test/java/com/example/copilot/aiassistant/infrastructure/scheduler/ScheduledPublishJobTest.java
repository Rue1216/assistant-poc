package com.example.copilot.aiassistant.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.application.command.config.ConfigCommandUseCase;
import com.example.copilot.aiassistant.application.command.config.PublishCommand;
import com.example.copilot.aiassistant.application.command.config.PublishMode;
import com.example.copilot.aiassistant.application.command.config.SaveCommand;
import com.example.copilot.aiassistant.application.command.config.ScheduledPublishOutcome;
import com.example.copilot.aiassistant.application.config.PublishedConfigCache;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.config.SaveMode;
import com.example.copilot.aiassistant.domain.config.ScheduleWindow;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigDocument;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigDocumentMapper;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigRepositoryImpl;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.example.copilot.testsupport.ConfigDocumentStore;
import com.example.copilot.testsupport.LogCapture;
import com.example.copilot.testsupport.SpringAop;
import com.mongodb.MongoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.TransactionSystemException;

/**
 * The scheduler is disabled in tests; the job is built by hand around a use case with a movable clock, given the
 * container's transaction proxy so that each due version is published in its own transaction.
 */
class ScheduledPublishJobTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-20T02:00:00Z");
    private static final ConfigKey KEY = new ConfigKey("instruction.contact-prep");
    private static final String ACTOR = "EEE22222";

    @Autowired AssistantConfigRepository repository;
    ConfigDocumentStore mongoRepository;

    @Autowired
    void initMongoRepository(MongoTemplate template) {
        mongoRepository = new ConfigDocumentStore(template);
    }
    @Autowired MongoTemplate mongo;
    @Autowired AssistantConfigDocumentMapper mapper;
    @Autowired ConfigValidator validator;
    @Autowired ScheduleWindow window;
    @Autowired SeedLoader seedLoader;
    @Autowired PublishedConfigCache cache;
    @Autowired ApplicationContext context;

    private MutableClock clock;
    private ConfigCommandUseCase service;
    private ScheduledPublishJob job;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = newService(repository);
        job = new ScheduledPublishJob(service);
    }

    @AfterEach
    void restoreSeed() {
        MDC.remove("traceId");
        MDC.remove("spanId");
        mongoRepository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
        cache.invalidate();
    }

    private ConfigCommandUseCase newService(AssistantConfigRepository repo) {
        return SpringAop.advised(context, new ConfigCommandUseCase(repo, validator, context, clock, window));
    }

    private AssistantConfig reload(int version) {
        return repository.findVersion(KEY, version).orElseThrow();
    }

    private AssistantConfig schedule(Instant at) {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        return service.publish(KEY, new PublishCommand(PublishMode.SCHEDULED, at, draft.docVersion()), ACTOR);
    }

    private static List<Level> levels(List<LogEvent> events) {
        return events.stream().map(LogEvent::getLevel).toList();
    }

    private static List<String> messages(List<LogEvent> events) {
        return events.stream().map(e -> e.getMessage().getFormattedMessage()).toList();
    }

    @Test
    void schedulerIsOffInTestProfile() {
        assertThat(context.getBeansOfType(ScheduledPublishJob.class)).isEmpty();
    }

    @Test
    void publishesDueVersionKeepingSchedulerAsUpdatedBy() {
        AssistantConfig v1Before = reload(1);
        Instant at = NOW.plus(Duration.ofMinutes(2));
        schedule(at);
        clock.advance(Duration.ofMinutes(3));

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(ScheduledPublishJob.class, Level.DEBUG)) {
            job.run();
            events = capture.events();
        }

        AssistantConfig v2 = reload(2);
        assertThat(v2.status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(v2.updatedBy()).isEqualTo(ACTOR);
        assertThat(v2.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3)));
        assertThat(v2.scheduledAt()).isEqualTo(at);
        AssistantConfig v1 = reload(1);
        assertThat(v1.status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.updatedBy()).isEqualTo(v1Before.updatedBy());
        assertThat(v1.updatedAt()).isEqualTo(v1Before.updatedAt());
        assertThat(levels(events)).containsExactly(Level.INFO);
        assertThat(messages(events)).containsExactly("published 1 scheduled config version(s)");
    }

    @Test
    void notYetDueIsUntouched() {
        schedule(NOW.plus(Duration.ofMinutes(10)));
        AssistantConfig before = reload(2);
        clock.advance(Duration.ofMinutes(9));

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(ScheduledPublishJob.class, Level.DEBUG)) {
            job.run();
            events = capture.events();
        }

        AssistantConfig after = reload(2);
        assertThat(after.status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(after.docVersion()).isEqualTo(before.docVersion());
        assertThat(after.updatedAt()).isEqualTo(before.updatedAt());
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(events).isEmpty();
    }

    /** Two instances run at once: the version is published once, the loser logs nothing above DEBUG. */
    @Test
    void concurrentRunsPublishOnce() throws Exception {
        schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));

        int threads = 2;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(ScheduledPublishJob.class, Level.DEBUG)) {
            List<Future<?>> runs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                runs.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    job.run();
                    return null;
                }));
            }
            for (Future<?> run : runs) {
                run.get(30, TimeUnit.SECONDS);
            }
            events = capture.events();
        } finally {
            pool.shutdownNow();
        }

        assertThat(mongoRepository.findByConfigKeyOrderByVersionDesc(KEY.value()))
                .filteredOn(c -> c.getStatus() == ConfigStatus.PUBLISHED)
                .extracting(AssistantConfigDocument::getVersion).containsExactly(2);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(levels(events)).doesNotContain(Level.WARN, Level.ERROR);
        assertThat(messages(events)).filteredOn(m -> m.startsWith("published "))
                .containsExactly("published 1 scheduled config version(s)");
    }

    @Test
    void saveAfterJobPublishedReturnsConflict() {
        AssistantConfig scheduled = schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));
        job.run();
        String published = reload(2).content();

        SaveCommand stale = new SaveCommand("late save", published + "\n- late edit\n", "note", SaveMode.KEEP_STATUS,
                scheduled.docVersion());
        assertThatThrownBy(() -> service.saveWorkingCopy(KEY, stale, "EEE11111"))
                .isInstanceOfSatisfying(AssistantException.class, e -> assertThat(e.code())
                        .isEqualTo(ErrorCode.AI_CONFIG_CONFLICT));

        AssistantConfig after = reload(2);
        assertThat(after.status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(after.content()).isEqualTo(published);
        assertThat(after.name()).isNotEqualTo("late save");
    }

    @Test
    void discardAfterJobPublishedReturnsConflict() {
        AssistantConfig scheduled = schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));
        job.run();

        assertThatThrownBy(() -> service.discardDraft(KEY, scheduled.docVersion(), ACTOR))
                .isInstanceOfSatisfying(AssistantException.class, e -> assertThat(e.code())
                        .isEqualTo(ErrorCode.AI_CONFIG_CONFLICT));
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.PUBLISHED);
    }

    @Test
    void publishAfterOtherAdminPublishedReturnsConflict() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        service.publish(KEY, new PublishCommand(PublishMode.IMMEDIATE, null, draft.docVersion()), "admin.a");

        assertThatThrownBy(() -> service.publish(KEY,
                new PublishCommand(PublishMode.IMMEDIATE, null, draft.docVersion()), "admin.b"))
                .isInstanceOfSatisfying(AssistantException.class, e -> assertThat(e.code())
                        .isEqualTo(ErrorCode.AI_CONFIG_CONFLICT));
        assertThat(reload(2).updatedBy()).isEqualTo("admin.a");
    }

    @Test
    void cacheInvalidatedAfterPublish() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        AssistantConfig saved = service.saveWorkingCopy(KEY,
                new SaveCommand("renamed v2", draft.content(), "note", SaveMode.KEEP_STATUS,
                        draft.docVersion()), ACTOR);
        service.publish(KEY, new PublishCommand(PublishMode.SCHEDULED, NOW.plus(Duration.ofMinutes(2)),
                saved.docVersion()), ACTOR);
        String before = nameInCache();
        assertThat(before).isNotEqualTo("renamed v2");
        clock.advance(Duration.ofMinutes(3));

        job.run();

        assertThat(nameInCache()).isEqualTo("renamed v2");
    }

    /**
     * Runs the job over a repository whose archive read fails, and returns what the job, the use case and the
     * repository logged (a lost race is logged by the job at DEBUG, any other failure at WARN).
     */
    private List<LogEvent> runWithFindFailure(RuntimeException failure) {
        schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));
        MongoTemplate failing = mock(MongoTemplate.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(mongo)));
        doThrow(failure).when(failing).findOne(any(Query.class), eq(AssistantConfigDocument.class));
        ScheduledPublishJob racing = new ScheduledPublishJob(newService(new AssistantConfigRepositoryImpl(failing, mapper)));
        List<LogEvent> events = new ArrayList<>();
        try (LogCapture jobLog = LogCapture.of(ScheduledPublishJob.class, Level.DEBUG);
             LogCapture serviceLog = LogCapture.of(ConfigCommandUseCase.class, Level.DEBUG);
             LogCapture repositoryLog = LogCapture.of(AssistantConfigRepositoryImpl.class, Level.DEBUG)) {
            racing.run();
            events.addAll(jobLog.events());
            events.addAll(serviceLog.events());
            events.addAll(repositoryLog.events());
        }
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
        return events;
    }

    private static MongoException labelled(int code, String label) {
        MongoException e = new MongoException(code, "boom");
        e.addLabel(label);
        return e;
    }

    @Test
    void writeConflictOnClaimIsLoggedAtDebug() {
        List<LogEvent> events = runWithFindFailure(
                new DataIntegrityViolationException("x", new MongoException(112, "conflict")));

        assertThat(levels(events)).containsExactly(Level.DEBUG);
        assertThat(messages(events).getFirst()).contains("lost race", KEY.value());
    }

    @Test
    void labelledNonConflictFailureIsLoggedAtWarnWithoutMessage() {
        List<LogEvent> events = runWithFindFailure(new TransactionSystemException("secret-detail",
                labelled(91, MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)));

        assertThat(levels(events)).containsExactly(Level.WARN);
        assertThat(events.get(0).getMessage().getFormattedMessage()).contains(KEY.value(), "TransactionSystemException")
                .doesNotContain("secret-detail");
    }

    /** The version was published by another run between this run's query and its claim. */
    @Test
    void staleClaimPublishesNothingAndLogsNoWarn() {
        AssistantConfig scheduled = schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));
        AssistantConfigRepository stale = mock(AssistantConfigRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
        doAnswer(inv -> {
            List<AssistantConfig> due = repository.findScheduledDue(inv.getArgument(0));
            job.run();
            return due;
        }).when(stale).findScheduledDue(any());

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(ScheduledPublishJob.class, Level.DEBUG)) {
            new ScheduledPublishJob(newService(stale)).run();
            events = capture.events();
        }

        // The other run's INFO, then this run's lost race; this run publishes nothing.
        assertThat(levels(events)).containsExactly(Level.INFO, Level.DEBUG);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(scheduled.version()).isEqualTo(2);
    }

    /** Moved back from AssistantConfigRepositoryImplTest#lostRaceClassification (was its isLostRace). */
    @Test
    void lostRaceClassification() {
        assertThat(ScheduledPublishJob.isLostRace(new AssistantException(ErrorCode.AI_CONFIG_CONFLICT))).isTrue();
        assertThat(ScheduledPublishJob.isLostRace(new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND))).isFalse();
        assertThat(ScheduledPublishJob.isLostRace(new RuntimeException(new MongoException(112, "w")))).isTrue();
        assertThat(ScheduledPublishJob.isLostRace(new RuntimeException(new MongoException(11000, "d")))).isTrue();
        assertThat(ScheduledPublishJob.isLostRace(new RuntimeException(
                labelled(91, MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)))).isFalse();
        assertThat(ScheduledPublishJob.isLostRace(new RuntimeException(
                labelled(1, MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)))).isFalse();
        assertThat(ScheduledPublishJob.isLostRace(new RuntimeException("other"))).isFalse();
        // A conflict translated at the use-case boundary is judged by the Mongo failure it carries.
        assertThat(ScheduledPublishJob.isLostRace(translated(new MongoException(112, "w")))).isTrue();
        assertThat(ScheduledPublishJob.isLostRace(translated(new MongoException(11000, "d")))).isTrue();
        assertThat(ScheduledPublishJob.isLostRace(translated(
                labelled(91, MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)))).isFalse();
        assertThat(ScheduledPublishJob.isLostRace(translated(
                labelled(1, MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)))).isFalse();
    }

    private static AssistantException translated(MongoException cause) {
        AssistantException e = new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        e.initCause(new TransactionSystemException("commit", cause));
        return e;
    }

    private static AssistantConfig due(String key, int version) {
        return AssistantConfig.newDraft(new ConfigKey(key), ConfigType.SYSTEM_INSTRUCTION, "n", "c", version, ACTOR,
                NOW).withStatus(ConfigStatus.SCHEDULED, NOW);
    }

    /** Each due version is tried on its own: a failure is logged and the run goes on; only PUBLISHED counts. */
    @Test
    void oneFailureDoesNotStopTheRun() {
        AssistantConfig failing = due("instruction.a", 2);
        AssistantConfig reverted = due("instruction.b", 3);
        AssistantConfig published = due("instruction.c", 4);
        ConfigCommandUseCase useCase = mock(ConfigCommandUseCase.class);
        when(useCase.findScheduledDue()).thenReturn(List.of(failing, reverted, published));
        when(useCase.publishScheduled(failing)).thenThrow(new IllegalStateException("secret"));
        when(useCase.publishScheduled(reverted)).thenReturn(ScheduledPublishOutcome.REVERTED);
        when(useCase.publishScheduled(published)).thenReturn(ScheduledPublishOutcome.PUBLISHED);

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(ScheduledPublishJob.class, Level.DEBUG)) {
            new ScheduledPublishJob(useCase).run();
            events = capture.events();
        }

        assertThat(levels(events)).containsExactly(Level.WARN, Level.INFO);
        assertThat(messages(events)).containsExactly(
                "scheduled publish failed configKey=instruction.a version=2 error=java.lang.IllegalStateException",
                "published 1 scheduled config version(s)");
    }

    @Test
    void runSwallowsFailures() {
        ConfigCommandUseCase broken = mock(ConfigCommandUseCase.class);
        doThrow(new IllegalStateException("secret")).when(broken).findScheduledDue();

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(ScheduledPublishJob.class, Level.DEBUG)) {
            new ScheduledPublishJob(broken).run();
            events = capture.events();
        }

        assertThat(levels(events)).containsExactly(Level.WARN);
        assertThat(messages(events)).containsExactly("scheduled publish run failed error=java.lang.IllegalStateException");
    }

    @Test
    void runsUnderAFreshTraceIdAndClearsItAfterwards() {
        ConfigCommandUseCase spy = mock(ConfigCommandUseCase.class);
        List<String> seen = new ArrayList<>();
        doAnswer(inv -> {
            seen.add(MDC.get("traceId"));
            seen.add(MDC.get("spanId"));
            return List.of();
        }).when(spy).findScheduledDue();
        ScheduledPublishJob traced = new ScheduledPublishJob(spy);

        traced.run();
        traced.run();

        assertThat(seen).hasSize(4).allSatisfy(id -> assertThat(id).matches("[0-9a-f]{32}"));
        assertThat(seen.get(0)).isNotEqualTo(seen.get(2));
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

    @Test
    void traceIdIsClearedWhenTheRunThrows() {
        ConfigCommandUseCase broken = mock(ConfigCommandUseCase.class);
        doThrow(new IllegalStateException("secret")).when(broken).findScheduledDue();

        new ScheduledPublishJob(broken).run();

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

    private String nameInCache() {
        return cache.current().instructions().stream()
                .filter(c -> c.configKey().equals(KEY)).findFirst().orElseThrow().name();
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
