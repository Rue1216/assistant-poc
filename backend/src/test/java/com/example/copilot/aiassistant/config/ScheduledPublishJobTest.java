package com.example.copilot.aiassistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.common.AssistantException;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.prompt.PublishedConfigCache;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.MongoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;

/** The scheduler is disabled in tests; the job is built by hand around a service with a movable clock. */
class ScheduledPublishJobTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-20T02:00:00Z");
    private static final String KEY = "instruction.contact-prep";
    private static final String ACTOR = "it.admin";

    @Autowired AssistantConfigRepository repository;
    @Autowired MongoTemplate mongo;
    @Autowired ConfigValidator validator;
    @Autowired MongoTransactionManager transactionManager;
    @Autowired AiAssistantProperties properties;
    @Autowired SeedLoader seedLoader;
    @Autowired PublishedConfigCache cache;
    @Autowired ApplicationContext context;

    private MutableClock clock;
    private ConfigService service;
    private ScheduledPublishJob job;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = new ConfigService(repository, mongo, validator, cache, transactionManager, clock, properties);
        job = new ScheduledPublishJob(service);
    }

    @AfterEach
    void restoreSeed() {
        repository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
        cache.invalidate();
    }

    private AssistantConfig reload(int version) {
        return repository.findByConfigKeyAndVersion(KEY, version).orElseThrow();
    }

    private AssistantConfig schedule(Instant at) {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        return service.publish(KEY, new PublishCommand(PublishMode.SCHEDULED, at, draft.getDocVersion()), ACTOR);
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
        assertThat(v2.getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(v2.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(v2.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3)));
        assertThat(v2.getScheduledAt()).isEqualTo(at);
        AssistantConfig v1 = reload(1);
        assertThat(v1.getStatus()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.getUpdatedBy()).isEqualTo(v1Before.getUpdatedBy());
        assertThat(v1.getUpdatedAt()).isEqualTo(v1Before.getUpdatedAt());
    }

    @Test
    void notYetDueIsUntouched() {
        schedule(NOW.plus(Duration.ofMinutes(10)));
        AssistantConfig before = reload(2);
        clock.advance(Duration.ofMinutes(9));

        job.run();

        AssistantConfig after = reload(2);
        assertThat(after.getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(after.getDocVersion()).isEqualTo(before.getDocVersion());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
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

        assertThat(repository.findByConfigKeyOrderByVersionDesc(KEY))
                .filteredOn(c -> c.getStatus() == ConfigStatus.PUBLISHED)
                .extracting(AssistantConfig::getVersion).containsExactly(2);
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.ARCHIVED);
    }

    @Test
    void saveAfterJobPublishedReturnsConflict() {
        AssistantConfig scheduled = schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));
        job.run();
        String published = reload(2).getContent();

        SaveCommand stale = new SaveCommand("late save", published + "\n- late edit\n", "note", SaveMode.KEEP_STATUS,
                scheduled.getDocVersion());
        assertThatThrownBy(() -> service.saveWorkingCopy(KEY, stale, "backoffice.admin"))
                .isInstanceOfSatisfying(AssistantException.class, e -> assertThat(e.code())
                        .isIn(ErrorCode.AI_CONFIG_NOT_FOUND, ErrorCode.AI_CONFIG_CONFLICT));

        AssistantConfig after = reload(2);
        assertThat(after.getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(after.getContent()).isEqualTo(published);
        assertThat(after.getName()).isNotEqualTo("late save");
    }

    @Test
    void cacheInvalidatedAfterPublish() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        AssistantConfig saved = service.saveWorkingCopy(KEY,
                new SaveCommand("renamed v2", draft.getContent(), "note", SaveMode.KEEP_STATUS,
                        draft.getDocVersion()), ACTOR);
        service.publish(KEY, new PublishCommand(PublishMode.SCHEDULED, NOW.plus(Duration.ofMinutes(2)),
                saved.getDocVersion()), ACTOR);
        String before = nameInCache();
        assertThat(before).isNotEqualTo("renamed v2");
        clock.advance(Duration.ofMinutes(3));

        job.run();

        assertThat(nameInCache()).isEqualTo("renamed v2");
    }

    private List<ILoggingEvent> publishDueWithFindFailure(RuntimeException failure) {
        schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));
        AssistantConfigRepository failing = mock(AssistantConfigRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
        doThrow(failure).when(failing).findByConfigKeyAndStatus(any(), any());
        ConfigService racing = new ConfigService(failing, mongo, validator, cache, transactionManager, clock,
                properties);
        Logger logger = (Logger) LoggerFactory.getLogger(ConfigService.class);
        Level old = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            assertThat(racing.publishDue()).isZero();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(old);
        }
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        return appender.list;
    }

    private static MongoException labelled(int code, String label) {
        MongoException e = new MongoException(code, "boom");
        e.addLabel(label);
        return e;
    }

    @Test
    void writeConflictOnClaimIsLoggedAtDebug() {
        List<ILoggingEvent> events = publishDueWithFindFailure(
                new DataIntegrityViolationException("x", new MongoException(112, "conflict")));

        assertThat(events).extracting(ILoggingEvent::getLevel).containsExactly(Level.DEBUG);
    }

    @Test
    void labelledNonConflictFailureIsLoggedAtWarnWithoutMessage() {
        List<ILoggingEvent> events = publishDueWithFindFailure(new TransactionSystemException("secret-detail",
                labelled(91, MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)));

        assertThat(events).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
        assertThat(events.get(0).getFormattedMessage()).contains(KEY, "TransactionSystemException")
                .doesNotContain("secret-detail");
    }

    @Test
    void lostRaceClassification() {
        assertThat(ConfigService.isLostRace(new AssistantException(ErrorCode.AI_CONFIG_CONFLICT))).isTrue();
        assertThat(ConfigService.isLostRace(new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND))).isFalse();
        assertThat(ConfigService.isLostRace(new RuntimeException(new MongoException(112, "w")))).isTrue();
        assertThat(ConfigService.isLostRace(new RuntimeException(new MongoException(11000, "d")))).isTrue();
        assertThat(ConfigService.isLostRace(new RuntimeException(
                labelled(91, MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)))).isFalse();
        assertThat(ConfigService.isLostRace(new RuntimeException(
                labelled(1, MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)))).isFalse();
        assertThat(ConfigService.isLostRace(new RuntimeException("other"))).isFalse();
    }

    @Test
    void staleClaimReturnsZeroWithoutPublishing() {
        AssistantConfig scheduled = schedule(NOW.plus(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));
        AssistantConfigRepository stale = mock(AssistantConfigRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
        doAnswer(inv -> {
            List<AssistantConfig> due = repository.findByStatusAndScheduledAtLessThanEqual(
                    inv.getArgument(0), inv.getArgument(1));
            service.publishDue();
            return due;
        }).when(stale).findByStatusAndScheduledAtLessThanEqual(any(), any());

        int second = new ConfigService(stale, mongo, validator, cache, transactionManager, clock, properties)
                .publishDue();

        assertThat(second).isZero();
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(scheduled.getVersion()).isEqualTo(2);
    }

    @Test
    void runSwallowsFailures() {
        ConfigService broken = mock(ConfigService.class);
        doThrow(new IllegalStateException("secret")).when(broken).publishDue();

        new ScheduledPublishJob(broken).run();
    }

    private String nameInCache() {
        return cache.current().instructions().stream()
                .filter(c -> c.getConfigKey().equals(KEY)).findFirst().orElseThrow().getName();
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
