package com.example.copilot.aiassistant.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.application.config.ConfigCommandService;
import com.example.copilot.aiassistant.application.config.PublishCommand;
import com.example.copilot.aiassistant.application.config.PublishMode;
import com.example.copilot.aiassistant.application.config.SaveCommand;
import com.example.copilot.aiassistant.application.config.SaveMode;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.application.config.PublishedConfigCache;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigDocument;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigDocumentMapper;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigRepositoryImpl;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.example.copilot.testsupport.LogCapture;
import com.example.copilot.testsupport.ConfigDocumentStore;
import com.mongodb.MongoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** The scheduler is disabled in tests; the job is built by hand around a service with a movable clock. */
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
    @Autowired MongoTransactionManager transactionManager;
    @Autowired AiAssistantProperties properties;
    @Autowired SeedLoader seedLoader;
    @Autowired PublishedConfigCache cache;
    @Autowired ApplicationContext context;

    private MutableClock clock;
    private ConfigCommandService service;
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

    private ConfigCommandService newService(AssistantConfigRepository repo) {
        return new ConfigCommandService(repo, validator, cache, clock, properties);
    }

    private AssistantConfig reload(int version) {
        return repository.findVersion(KEY, version).orElseThrow();
    }

    private AssistantConfig schedule(Instant at) {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        return service.publish(KEY, new PublishCommand(PublishMode.SCHEDULED, at, draft.docVersion()), ACTOR);
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

        job.run();

        AssistantConfig v2 = reload(2);
        assertThat(v2.status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(v2.updatedBy()).isEqualTo(ACTOR);
        assertThat(v2.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3)));
        assertThat(v2.scheduledAt()).isEqualTo(at);
        AssistantConfig v1 = reload(1);
        assertThat(v1.status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.updatedBy()).isEqualTo(v1Before.updatedBy());
        assertThat(v1.updatedAt()).isEqualTo(v1Before.updatedAt());
    }

    @Test
    void notYetDueIsUntouched() {
        schedule(NOW.plus(Duration.ofMinutes(10)));
        AssistantConfig before = reload(2);
        clock.advance(Duration.ofMinutes(9));

        job.run();

        AssistantConfig after = reload(2);
        assertThat(after.status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(after.docVersion()).isEqualTo(before.docVersion());
        assertThat(after.updatedAt()).isEqualTo(before.updatedAt());
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
    }

    @Test
    void concurrentRunsPublishOnce() throws Exception {
        schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));

        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return service.publishDue();
                    })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            int total = 0;
            for (Future<Integer> f : results) {
                total += f.get(30, TimeUnit.SECONDS);
            }
            assertThat(total).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(mongoRepository.findByConfigKeyOrderByVersionDesc(KEY.value()))
                .filteredOn(c -> c.getStatus() == ConfigStatus.PUBLISHED)
                .extracting(AssistantConfigDocument::getVersion).containsExactly(2);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.ARCHIVED);
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
     * Runs the service over a repository whose archive read fails, and returns what the service and the
     * repository logged (a lost race is logged by the repository, any other failure by the service).
     */
    private List<LogEvent> publishDueWithFindFailure(RuntimeException failure) {
        schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));
        MongoTemplate failing = mock(MongoTemplate.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(mongo)));
        doThrow(failure).when(failing).findOne(any(Query.class), eq(AssistantConfigDocument.class));
        ConfigCommandService racing = newService(new AssistantConfigRepositoryImpl(failing, mapper, transactionManager));
        List<LogEvent> events = new ArrayList<>();
        try (LogCapture serviceLog = LogCapture.of(ConfigCommandService.class, Level.DEBUG);
             LogCapture repositoryLog = LogCapture.of(AssistantConfigRepositoryImpl.class, Level.DEBUG)) {
            assertThat(racing.publishDue()).isZero();
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
        List<LogEvent> events = publishDueWithFindFailure(
                new DataIntegrityViolationException("x", new MongoException(112, "conflict")));

        assertThat(events).extracting(LogEvent::getLevel).containsExactly(Level.DEBUG);
    }

    @Test
    void labelledNonConflictFailureIsLoggedAtWarnWithoutMessage() {
        List<LogEvent> events = publishDueWithFindFailure(new TransactionSystemException("secret-detail",
                labelled(91, MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)));

        assertThat(events).extracting(LogEvent::getLevel).containsExactly(Level.WARN);
        assertThat(events.get(0).getMessage().getFormattedMessage()).contains(KEY.value(), "TransactionSystemException")
                .doesNotContain("secret-detail");
    }

    @Test
    void staleClaimReturnsZeroWithoutPublishing() {
        AssistantConfig scheduled = schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));
        AssistantConfigRepository stale = mock(AssistantConfigRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
        doAnswer(inv -> {
            List<AssistantConfig> due = repository.findScheduledDue(inv.getArgument(0));
            service.publishDue();
            return due;
        }).when(stale).findScheduledDue(any());

        int second = newService(stale).publishDue();

        assertThat(second).isZero();
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(scheduled.version()).isEqualTo(2);
    }

    @Test
    void runSwallowsFailures() {
        ConfigCommandService broken = mock(ConfigCommandService.class);
        doThrow(new IllegalStateException("secret")).when(broken).publishDue();

        new ScheduledPublishJob(broken).run();
    }

    @Test
    void runsUnderAFreshTraceIdAndClearsItAfterwards() {
        ConfigCommandService spy = mock(ConfigCommandService.class);
        List<String> seen = new ArrayList<>();
        doAnswer(inv -> {
            seen.add(MDC.get("traceId"));
            seen.add(MDC.get("spanId"));
            return 0;
        }).when(spy).publishDue();
        ScheduledPublishJob traced = new ScheduledPublishJob(spy);

        traced.run();
        traced.run();

        assertThat(seen).hasSize(4).allSatisfy(id -> assertThat(id).matches("[0-9a-f]{32}"));
        assertThat(seen.get(0)).isNotEqualTo(seen.get(2));
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

    @Test
    void traceIdIsClearedWhenPublishDueThrows() {
        ConfigCommandService broken = mock(ConfigCommandService.class);
        doThrow(new IllegalStateException("secret")).when(broken).publishDue();

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
