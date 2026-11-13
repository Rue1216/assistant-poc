package com.example.copilot.aiassistant.application.command.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.application.config.PublishedConfigCache;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.config.SaveMode;
import com.example.copilot.aiassistant.domain.config.ScheduleWindow;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.infrastructure.mongo.AiAssistantCollections;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigDocument;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigDocumentMapper;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigRepositoryImpl;
import com.example.copilot.aiassistant.infrastructure.mongo.ConfigTransactionConflictAspect;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.example.copilot.testsupport.SpringAop;
import com.mongodb.MongoException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transaction boundaries of {@link ConfigCommandUseCase} (spec 5.3) against the real replica set, through the
 * container's proxy: rollback, one winner per scheduled version, cache invalidation after commit only, and
 * commit-time conflicts translated outside the transaction interceptor.
 */
class ConfigCommandUseCaseTransactionTest extends IntegrationTestBase {

    private static final ConfigKey KEY = new ConfigKey("instruction.contact-prep");
    private static final String ACTOR = "EEE22222";
    private static final String CONFIG = AiAssistantCollections.CONFIG;

    @MockitoSpyBean PublishedConfigCache cache;
    @Autowired ConfigCommandUseCase useCase;
    @Autowired AssistantConfigRepository repository;
    @Autowired AssistantConfigDocumentMapper mapper;
    @Autowired MongoTemplate mongo;
    @Autowired ConfigValidator validator;
    @Autowired ScheduleWindow window;
    @Autowired Clock clock;
    @Autowired SeedLoader seedLoader;
    @Autowired ApplicationContext context;

    @AfterEach
    void restoreSeed() {
        mongo.remove(new Query(), CONFIG);
        seedLoader.run(new DefaultApplicationArguments());
        cache.invalidate();
    }

    private ConfigCommandUseCase advisedUseCase(AssistantConfigRepository repo) {
        return SpringAop.advised(context, new ConfigCommandUseCase(repo, validator, context, clock, window));
    }

    private AssistantConfig reload(int version) {
        return repository.findVersion(KEY, version).orElseThrow();
    }

    private long countOf(ConfigStatus status) {
        return mongo.count(Query.query(where("configKey").is(KEY.value()).and("status").is(status)), CONFIG);
    }

    private static void assertConflict(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AssistantException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_CONFIG_CONFLICT));
    }

    private static PublishCommand immediate(AssistantConfig wc) {
        return new PublishCommand(PublishMode.IMMEDIATE, null, wc.docVersion());
    }

    private String nameInCache() {
        return cache.current().instructions().stream()
                .filter(c -> c.configKey().equals(KEY)).findFirst().orElseThrow().name();
    }

    private long onPublishedCalls() {
        return mockingDetails(cache).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("onPublished")).count();
    }

    /** A use case whose archive read sees the PUBLISHED version change right after reading it. */
    private ConfigCommandUseCase racingArchive() {
        MongoTemplate racing = mock(MongoTemplate.class, withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(mongo)));
        doAnswer(inv -> {
            AssistantConfigDocument read = mongo.findOne(inv.getArgument(0), AssistantConfigDocument.class);
            if (read != null) {
                mongo.updateFirst(Query.query(where("id").is(read.getId())), new Update().inc("docVersion", 1L),
                        AssistantConfigDocument.class);
            }
            return read;
        }).when(racing).findOne(any(Query.class), eq(AssistantConfigDocument.class));
        return advisedUseCase(new AssistantConfigRepositoryImpl(racing, mapper));
    }

    /** A use case whose immediate publish runs {@code hook} in the transaction's beforeCommit callback. */
    private ConfigCommandUseCase withBeforeCommit(Runnable hook) {
        AssistantConfigRepository hooked = mock(AssistantConfigRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
        doAnswer(inv -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    hook.run();
                }
            });
            return repository.publish(inv.getArgument(0));
        }).when(hooked).publish(any());
        return advisedUseCase(hooked);
    }

    @Test
    void publishRollsBackWhenArchiveFails() {
        AssistantConfig v1Before = reload(1);
        AssistantConfig draft = useCase.createDraft(KEY, ACTOR);

        assertConflict(() -> racingArchive().publish(KEY, immediate(draft), ACTOR));

        // The docVersion bump ran inside the publishing transaction, so it was rolled back with everything else.
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(1).docVersion()).isEqualTo(v1Before.docVersion());
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());
    }

    @Test
    void concurrentPublishScheduledPublishesOnce() throws Exception {
        AssistantConfig draft = useCase.createDraft(KEY, ACTOR);
        repository.save(draft.withStatus(ConfigStatus.SCHEDULED, clock.instant().minus(Duration.ofMinutes(1))));
        AssistantConfig due = useCase.findScheduledDue().getFirst();

        int threads = 2;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<ScheduledPublishOutcome> outcomes = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        try {
            List<Future<ScheduledPublishOutcome>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return useCase.publishScheduled(due);
                }));
            }
            for (Future<ScheduledPublishOutcome> result : results) {
                try {
                    outcomes.add(result.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).containsExactly(ScheduledPublishOutcome.PUBLISHED);
        assertThat(failures).singleElement().isInstanceOfSatisfying(AssistantException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_CONFIG_CONFLICT));
        assertThat(countOf(ConfigStatus.PUBLISHED)).isEqualTo(1);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(countOf(ConfigStatus.ARCHIVED)).isEqualTo(1);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.ARCHIVED);
        verify(cache, times(1)).onPublished(new ConfigPublishedEvent(KEY));
    }

    @Test
    void cacheInvalidatedOnlyAfterCommit() {
        String oldName = nameInCache();
        AssistantConfig draft = useCase.createDraft(KEY, ACTOR);
        AssistantConfig renamed = useCase.saveWorkingCopy(KEY, new SaveCommand("renamed v2", draft.content(), "note",
                SaveMode.KEEP_STATUS, draft.docVersion()), ACTOR);
        assertThat(nameInCache()).isEqualTo(oldName);
        long[] callsBeforeCommit = {-1};

        withBeforeCommit(() -> callsBeforeCommit[0] = onPublishedCalls()).publish(KEY, immediate(renamed), ACTOR);

        // The event was published inside the transaction, but the listener only ran once it had committed.
        assertThat(callsBeforeCommit[0]).isZero();
        verify(cache).onPublished(new ConfigPublishedEvent(KEY));
        assertThat(nameInCache()).isEqualTo("renamed v2");
    }

    @Test
    void scheduledPublishDoesNotInvalidateCache() {
        AssistantConfig draft = useCase.createDraft(KEY, ACTOR);

        useCase.publish(KEY, new PublishCommand(PublishMode.SCHEDULED,
                clock.instant().plus(window.minLead()).plus(Duration.ofMinutes(5)), draft.docVersion()), ACTOR);

        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
        verify(cache, never()).onPublished(any());
        verify(cache, never()).invalidate();
    }

    @Test
    void cacheKeptWhenPublishRollsBack() {
        String oldName = nameInCache();
        AssistantConfig draft = useCase.createDraft(KEY, ACTOR);
        AssistantConfig renamed = useCase.saveWorkingCopy(KEY, new SaveCommand("renamed v2", draft.content(), "note",
                SaveMode.KEEP_STATUS, draft.docVersion()), ACTOR);

        assertConflict(() -> racingArchive().publish(KEY, immediate(renamed), ACTOR));

        verify(cache, never()).onPublished(any());
        verify(cache, never()).invalidate();
        assertThat(nameInCache()).isEqualTo(oldName);
    }

    /**
     * Fails the commit itself (after the method returned, inside the transaction interceptor): only an aspect
     * wrapped around the interceptor sees it, so this pins the aspect's order on the container's proxy.
     */
    @Test
    void commitTimeConflictThroughTheProxyBecomesConfigConflict() {
        AssistantConfig draft = useCase.createDraft(KEY, ACTOR);
        MongoException unknownCommit = new MongoException(50, "commit result unknown");
        unknownCommit.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        ConfigCommandUseCase failingCommit = withBeforeCommit(() -> {
            throw new TransactionSystemException("Could not commit Mongo transaction", unknownCommit);
        });

        assertConflict(() -> failingCommit.publish(KEY, immediate(draft), ACTOR));

        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());
        verify(cache, never()).onPublished(any());
    }

    @Test
    void commitTimeConflictBecomesConfigConflict() throws Throwable {
        ConfigTransactionConflictAspect aspect = new ConfigTransactionConflictAspect();

        TransactionSystemException writeConflict = new TransactionSystemException("x",
                new MongoException(112, "WriteConflict"));
        AssistantException translated = catchFrom(aspect, writeConflict);
        assertThat(translated.code()).isEqualTo(ErrorCode.AI_CONFIG_CONFLICT);
        assertThat(translated.getCause()).isSameAs(writeConflict);

        MongoException transientError = new MongoException(91, "shutdown");
        transientError.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        assertThat(catchFrom(aspect, new TransactionSystemException("y", transientError)).code())
                .isEqualTo(ErrorCode.AI_CONFIG_CONFLICT);
        assertThat(catchFrom(aspect, new DataIntegrityViolationException("z", new MongoException(11000, "dup")))
                .code()).isEqualTo(ErrorCode.AI_CONFIG_CONFLICT);

        IllegalStateException other = new IllegalStateException("other");
        assertThatThrownBy(() -> aspect.translateConflicts(throwing(other))).isSameAs(other);
        DataIntegrityViolationException validation = new DataIntegrityViolationException("v",
                new MongoException(121, "Document failed validation"));
        assertThatThrownBy(() -> aspect.translateConflicts(throwing(validation))).isSameAs(validation);
        AssistantException notFound = new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND);
        assertThatThrownBy(() -> aspect.translateConflicts(throwing(notFound))).isSameAs(notFound);

        ProceedingJoinPoint ok = mock(ProceedingJoinPoint.class);
        when(ok.proceed()).thenReturn("result");
        assertThat(aspect.translateConflicts(ok)).isEqualTo("result");
    }

    private static AssistantException catchFrom(ConfigTransactionConflictAspect aspect, RuntimeException failure)
            throws Throwable {
        ProceedingJoinPoint pjp = throwing(failure);
        try {
            aspect.translateConflicts(pjp);
        } catch (AssistantException e) {
            return e;
        }
        throw new AssertionError("expected AssistantException for " + failure);
    }

    private static ProceedingJoinPoint throwing(RuntimeException failure) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenThrow(failure);
        return pjp;
    }
}
