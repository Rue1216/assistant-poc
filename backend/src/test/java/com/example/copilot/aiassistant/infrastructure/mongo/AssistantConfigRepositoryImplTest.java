package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.application.command.config.ApplyCommand;
import com.example.copilot.aiassistant.application.command.config.ConfigCommandUseCase;
import com.example.copilot.aiassistant.application.command.config.PublishCommand;
import com.example.copilot.aiassistant.application.command.config.PublishMode;
import com.example.copilot.aiassistant.application.command.config.ScheduledPublishOutcome;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.config.ScheduleWindow;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.example.copilot.testsupport.LogCapture;
import com.example.copilot.testsupport.SpringAop;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.TransactionSystemException;

/**
 * Writes and conflict mapping of the config repository, against the real replica set. Mongo failures are injected
 * through a delegating {@link MongoTemplate} handed to a hand-built implementation. The multi-document writes rely
 * on the caller's transaction, so their atomicity is checked through a {@link ConfigCommandUseCase} carrying the
 * container's transaction proxy.
 */
class AssistantConfigRepositoryImplTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-20T02:00:00Z");
    private static final ConfigKey KEY = new ConfigKey("instruction.contact-prep");
    private static final String ACTOR = "EEE22222";
    private static final String OTHER = "EEE11111";
    private static final String CONFIG = AiAssistantCollections.CONFIG;
    private static final String TOO_LONG_NAME = "名".repeat(51);

    @Autowired MongoClient mongoClient;
    @Autowired AssistantConfigDocumentMapper mapper;
    @Autowired MongoTemplate mongo;
    @Autowired SeedLoader seedLoader;
    @Autowired ConfigValidator validator;
    @Autowired ScheduleWindow window;
    @Autowired ApplicationContext context;

    private AssistantConfigRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new AssistantConfigRepositoryImpl(mongo, mapper);
    }

    @AfterEach
    void restoreSeed() {
        mongo.remove(new Query(), AssistantConfigDocument.class);
        seedLoader.run(new DefaultApplicationArguments());
    }

    private AssistantConfigRepositoryImpl withMongoTemplate(MongoTemplate template) {
        return new AssistantConfigRepositoryImpl(template, mapper);
    }

    /** The use case over {@code repo}, at a fixed time, inside the container's transaction proxy. */
    private ConfigCommandUseCase useCase(AssistantConfigRepository repo, Instant now) {
        return SpringAop.advised(context,
                new ConfigCommandUseCase(repo, validator, context, Clock.fixed(now, ZoneOffset.UTC), window));
    }

    private static PublishCommand immediate(AssistantConfig wc) {
        return new PublishCommand(PublishMode.IMMEDIATE, null, wc.docVersion());
    }

    /** Delegates to the real repository unless a call is stubbed. */
    private AssistantConfigRepository delegatingRepository() {
        return mock(AssistantConfigRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
    }

    /** Delegates to the real MongoTemplate unless a call is stubbed. */
    private MongoTemplate delegatingMongoTemplate() {
        return mock(MongoTemplate.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(mongo)));
    }

    private AssistantConfig reload(int version) {
        return repository.findVersion(KEY, version).orElseThrow();
    }

    private long countOf(ConfigStatus status) {
        return mongo.count(Query.query(where("configKey").is(KEY.value()).and("status").is(status)), CONFIG);
    }

    private long versionCount() {
        return mongo.count(Query.query(where("configKey").is(KEY.value())), CONFIG);
    }

    private void bumpDocVersion(String id) {
        mongo.updateFirst(Query.query(where("id").is(id)), new Update().inc("docVersion", 1L),
                AssistantConfigDocument.class);
    }

    private AssistantConfig newDraft(int version, String actor) {
        AssistantConfig v1 = reload(1);
        return AssistantConfig.newDraft(KEY, v1.configType(), v1.name(), v1.content(), version, actor, NOW);
    }

    private AssistantConfig insertDraft(String actor) {
        return repository.insert(newDraft(2, actor));
    }

    private AssistantConfig scheduleDraft(Instant at) {
        return repository.save(insertDraft(ACTOR).withStatus(ConfigStatus.SCHEDULED, at));
    }

    private static void assertConflict(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AssistantException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_CONFIG_CONFLICT));
    }

    private static void assertValidationFailure(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().isInstanceOfSatisfying(MongoException.class, e -> assertThat(e.getCode()).isEqualTo(121));
    }

    private static MongoException labelled(int code, String label) {
        MongoException e = new MongoException(code, "boom");
        e.addLabel(label);
        return e;
    }

    @Test
    void insertDuplicateKeyThrowsConflict() {
        AssistantConfig inserted = insertDraft(ACTOR);
        assertThat(inserted.id()).isNotNull();
        assertThat(inserted.docVersion()).isZero();

        assertConflict(() -> repository.insert(newDraft(2, OTHER)));

        assertThat(versionCount()).isEqualTo(2);
        assertThat(reload(2).createdBy()).isEqualTo(ACTOR);
    }

    @Test
    void saveWithStaleDocVersionThrowsConflict() {
        AssistantConfig draft = insertDraft(OTHER);
        AssistantConfig saved = repository.save(draft.withContent("first save", draft.content(), "note")
                .touched(ACTOR, NOW.plusSeconds(5)));
        assertThat(saved.id()).isEqualTo(draft.id());
        assertThat(saved.docVersion()).isEqualTo(draft.docVersion() + 1);
        assertThat(saved.name()).isEqualTo("first save");

        assertConflict(() -> repository.save(draft.withContent("stale save", "x", null)));

        AssistantConfig after = reload(2);
        assertThat(after.name()).isEqualTo("first save");
        assertThat(after.updatedBy()).isEqualTo(ACTOR);
        assertThat(after.docVersion()).isEqualTo(saved.docVersion());
    }

    @Test
    void publishArchivesPreviousPublishedInOneTransaction() {
        AssistantConfig v1Before = reload(1);
        AssistantConfig draft = insertDraft(OTHER);
        Instant later = NOW.plus(Duration.ofMinutes(10));

        AssistantConfig published = useCase(repository, later).publish(KEY, immediate(draft), ACTOR);

        assertThat(published.status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(published.docVersion()).isEqualTo(draft.docVersion() + 1);
        assertThat(published.updatedBy()).isEqualTo(ACTOR);
        assertThat(published.updatedAt()).isEqualTo(later);
        AssistantConfig v1 = reload(1);
        assertThat(v1.status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.updatedBy()).isEqualTo(v1Before.updatedBy());
        assertThat(v1.updatedAt()).isEqualTo(v1Before.updatedAt());
        assertThat(v1.content()).isEqualTo(v1Before.content());
        assertThat(v1.docVersion()).isEqualTo(v1Before.docVersion() + 1);
        assertThat(countOf(ConfigStatus.PUBLISHED)).isEqualTo(1);
        assertThat(repository.findPublished(KEY)).map(AssistantConfig::version).contains(2);
    }

    /** Moved from ConfigCommandUseCaseTest#publishedVersionChangedBeforeArchiveIsConflict. */
    @Test
    void publishedVersionChangedBeforeArchiveIsConflictAndRollsBack() {
        AssistantConfig v1Before = reload(1);
        AssistantConfig draft = insertDraft(OTHER);
        MongoTemplate racing = delegatingMongoTemplate();
        doAnswer(inv -> {
            AssistantConfigDocument read = mongo.findOne(inv.getArgument(0), AssistantConfigDocument.class);
            if (read != null) {
                bumpDocVersion(read.getId());
            }
            return read;
        }).when(racing).findOne(any(Query.class), eq(AssistantConfigDocument.class));

        assertConflict(() -> useCase(withMongoTemplate(racing), NOW).publish(KEY, immediate(draft), ACTOR));

        // The bump ran inside the publishing transaction, so it was rolled back with everything else.
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(1).docVersion()).isEqualTo(v1Before.docVersion());
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());
    }

    @Test
    void publishWriteConflictWithAnotherTransactionIsConflict() {
        AssistantConfig draft = insertDraft(OTHER);

        try (ClientSession other = mongoClient.startSession()) {
            other.startTransaction();
            mongo.getCollection(CONFIG).updateOne(other,
                    Filters.and(Filters.eq("configKey", KEY.value()), Filters.eq("version", 1)),
                    Updates.set("changeNote", "uncommitted change by another transaction"));
            try {
                assertConflict(() -> useCase(repository, NOW).publish(KEY, immediate(draft), ACTOR));
            } finally {
                other.abortTransaction();
            }
        }

        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());
    }

    @Test
    void publishScheduledLosesRaceIsConflictAndRollsBackArchive() {
        AssistantConfig v1Before = reload(1);
        Instant at = NOW.plus(Duration.ofMinutes(2));
        scheduleDraft(at);
        Instant due = NOW.plus(Duration.ofMinutes(3));
        ConfigCommandUseCase scheduler = useCase(repository, due);
        AssistantConfig stale = scheduler.findScheduledDue().getFirst();

        // Someone saves the scheduled version between the scheduler's query and its conditional claim.
        repository.save(stale.withContent("saved meanwhile", stale.content(), "note").touched(OTHER, due));

        assertConflict(() -> scheduler.publishScheduled(stale));
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(1).docVersion()).isEqualTo(v1Before.docVersion());
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);

        // A fresh read claims it: published at the run time, the last editor stays the updatedBy.
        AssistantConfig fresh = scheduler.findScheduledDue().getFirst();
        assertThat(scheduler.publishScheduled(fresh)).isEqualTo(ScheduledPublishOutcome.PUBLISHED);
        AssistantConfig v2 = reload(2);
        assertThat(v2.status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(v2.updatedBy()).isEqualTo(OTHER);
        assertThat(v2.updatedAt()).isEqualTo(due);
        assertThat(v2.scheduledAt()).isEqualTo(at);
        assertThat(v2.docVersion()).isEqualTo(fresh.docVersion() + 1);
        AssistantConfig v1 = reload(1);
        assertThat(v1.status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.updatedAt()).isEqualTo(v1Before.updatedAt());
        assertThat(scheduler.findScheduledDue()).isEmpty();
        assertConflict(() -> scheduler.publishScheduled(fresh));
        assertThat(countOf(ConfigStatus.PUBLISHED)).isEqualTo(1);
        assertThat(countOf(ConfigStatus.ARCHIVED)).isEqualTo(1);
    }

    @Test
    void revertScheduledTurnsItBackIntoADraft() {
        scheduleDraft(NOW.plus(Duration.ofMinutes(2)));
        Instant due = NOW.plus(Duration.ofMinutes(3));
        AssistantConfig scheduled = repository.findScheduledDue(due).getFirst();

        assertThat(repository.revertScheduled(scheduled, AssistantConfig.SYSTEM_ACTOR, due)).isTrue();

        AssistantConfig reverted = reload(2);
        assertThat(reverted.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reverted.scheduledAt()).isNull();
        assertThat(reverted.updatedBy()).isEqualTo("system");
        assertThat(reverted.updatedAt()).isEqualTo(due);
        assertThat(reverted.docVersion()).isEqualTo(scheduled.docVersion() + 1);
    }

    @Test
    void revertScheduledSkipsAChangedVersion() {
        scheduleDraft(NOW.plus(Duration.ofMinutes(2)));
        Instant due = NOW.plus(Duration.ofMinutes(3));
        AssistantConfig stale = repository.findScheduledDue(due).getFirst();
        repository.save(stale.withContent("saved meanwhile", stale.content(), "note").touched(OTHER, due));
        AssistantConfig before = reload(2);

        assertThat(repository.revertScheduled(stale, AssistantConfig.SYSTEM_ACTOR, due)).isFalse();

        assertThat(reload(2)).isEqualTo(before);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
    }

    /**
     * Impl-level half of ScheduledPublishJobTest#writeConflictOnClaimIsLoggedAtDebug: the repository neither maps
     * nor logs a write conflict on the claim; the use case's boundary turns it into a conflict (keeping the cause)
     * and the job decides it is a lost race.
     */
    @Test
    void publishScheduledWriteConflictIsLostRaceLoggedAtDebug() {
        scheduleDraft(NOW.plus(Duration.ofMinutes(2)));
        Instant due = NOW.plus(Duration.ofMinutes(3));
        AssistantConfig scheduled = repository.findScheduledDue(due).getFirst();
        DataIntegrityViolationException writeConflict = new DataIntegrityViolationException("x",
                new MongoException(112, "conflict"));
        MongoTemplate failing = delegatingMongoTemplate();
        doThrow(writeConflict).when(failing).findOne(any(Query.class), eq(AssistantConfigDocument.class));

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(AssistantConfigRepositoryImpl.class, Level.DEBUG)) {
            assertThatThrownBy(() -> withMongoTemplate(failing).publishScheduled(scheduled, due))
                    .isSameAs(writeConflict);
            assertThatThrownBy(() -> useCase(withMongoTemplate(failing), due).publishScheduled(scheduled))
                    .isInstanceOfSatisfying(AssistantException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.AI_CONFIG_CONFLICT);
                        assertThat(e.getCause()).isSameAs(writeConflict);
                    });
            events = capture.events();
        }

        assertThat(events).isEmpty();
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
    }

    /** Impl-level half of ScheduledPublishJobTest#labelledNonConflictFailureIsLoggedAtWarnWithoutMessage. */
    @Test
    void publishScheduledNonRaceFailurePropagates() {
        scheduleDraft(NOW.plus(Duration.ofMinutes(2)));
        Instant due = NOW.plus(Duration.ofMinutes(3));
        AssistantConfig scheduled = repository.findScheduledDue(due).getFirst();
        TransactionSystemException failure = new TransactionSystemException("secret-detail",
                labelled(91, MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL));
        MongoTemplate failing = delegatingMongoTemplate();
        doThrow(failure).when(failing).findOne(any(Query.class), eq(AssistantConfigDocument.class));

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(AssistantConfigRepositoryImpl.class, Level.DEBUG)) {
            assertThatThrownBy(() -> withMongoTemplate(failing).publishScheduled(scheduled, due)).isSameAs(failure);
            events = capture.events();
        }

        assertThat(events).isEmpty();
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
    }

    @Test
    void validationFailure121Propagates() {
        AssistantConfig draft = insertDraft(OTHER);

        // Outside a transaction (insert, save) ...
        assertValidationFailure(() -> repository.insert(
                AssistantConfig.newDraft(new ConfigKey("instruction.too-long"), ConfigType.SYSTEM_INSTRUCTION, TOO_LONG_NAME,
                        "x", 1, ACTOR, NOW)));
        assertValidationFailure(() -> repository.save(draft.withContent(TOO_LONG_NAME, "x", null)));
        // ... and inside the publishing and applying transactions (the use case's, around the repository).
        AssistantConfigRepository corrupting = delegatingRepository();
        doAnswer(inv -> repository.findWorkingCopy(inv.getArgument(0))
                .map(c -> c.withContent(TOO_LONG_NAME, c.content(), c.changeNote())))
                .when(corrupting).findWorkingCopy(any());
        doAnswer(inv -> repository.findVersion(inv.getArgument(0), inv.getArgument(1))
                .map(c -> c.withContent(TOO_LONG_NAME, c.content(), c.changeNote())))
                .when(corrupting).findVersion(any(), eq(1));
        ConfigCommandUseCase corruptingUseCase = useCase(corrupting, NOW);
        assertValidationFailure(() -> corruptingUseCase.publish(KEY, immediate(draft), ACTOR));
        assertValidationFailure(() -> corruptingUseCase.applyVersion(KEY,
                new ApplyCommand(1, draft.docVersion(), true), ACTOR));

        assertThat(repository.findLatest(new ConfigKey("instruction.too-long"))).isEmpty();
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());
        assertThat(versionCount()).isEqualTo(2);
    }

    @Test
    void replaceWorkingCopyDiscardsOldAndInsertsDraftAtomically() {
        // Without a working copy only the draft is inserted.
        AssistantConfig first = repository.replaceWorkingCopy(null, newDraft(2, OTHER).withBasedOnVersion(1));
        assertThat(first.id()).isNotNull();
        assertThat(first.docVersion()).isZero();
        assertThat(first.basedOnVersion()).isEqualTo(1);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);

        Instant later = NOW.plus(Duration.ofMinutes(4));
        AssistantConfig applied = repository.replaceWorkingCopy(
                first.withStatus(ConfigStatus.DISCARDED, first.scheduledAt()).touched(ACTOR, later),
                AssistantConfig.newDraft(KEY, first.configType(), first.name(), first.content(), 3, ACTOR, later)
                        .withBasedOnVersion(1));

        assertThat(applied.version()).isEqualTo(3);
        assertThat(applied.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(applied.basedOnVersion()).isEqualTo(1);
        assertThat(applied.docVersion()).isZero();
        AssistantConfig replaced = reload(2);
        assertThat(replaced.status()).isEqualTo(ConfigStatus.DISCARDED);
        assertThat(replaced.createdBy()).isEqualTo(OTHER);
        assertThat(replaced.updatedBy()).isEqualTo(ACTOR);
        assertThat(replaced.updatedAt()).isEqualTo(later);
        assertThat(replaced.docVersion()).isEqualTo(first.docVersion() + 1);
        assertThat(repository.findWorkingCopy(KEY)).map(AssistantConfig::version).contains(3);
    }

    @Test
    void replaceWorkingCopyWithStaleDiscardedIsConflict() {
        AssistantConfig draft = insertDraft(OTHER);
        repository.save(draft.withContent("saved meanwhile", draft.content(), null));

        assertConflict(() -> repository.replaceWorkingCopy(draft.withStatus(ConfigStatus.DISCARDED, null),
                newDraft(3, ACTOR).withBasedOnVersion(1)));

        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).name()).isEqualTo("saved meanwhile");
        assertThat(versionCount()).isEqualTo(2);
    }

    /** Moved from ConfigCommandUseCaseTest#applyIsAtomic; the transaction is the use case's. */
    @Test
    void replaceWorkingCopyFailureRollsBackDiscard() {
        MongoTemplate failing = delegatingMongoTemplate();
        DataAccessResourceFailureException boom = new DataAccessResourceFailureException("insert failed");
        doThrow(boom).when(failing).insert(any(AssistantConfigDocument.class));
        doThrow(boom).when(failing).save(argThat((AssistantConfigDocument d) -> d != null && d.getId() == null));
        ConfigCommandUseCase failingUseCase = useCase(withMongoTemplate(failing), NOW);

        AssistantConfig draft = insertDraft(OTHER);
        assertThatThrownBy(() -> failingUseCase.applyVersion(KEY,
                new ApplyCommand(1, draft.docVersion(), true), ACTOR)).isSameAs(boom);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());

        AssistantConfig scheduled = repository.save(
                draft.withStatus(ConfigStatus.SCHEDULED, NOW.plus(Duration.ofHours(1))).touched(OTHER, NOW));
        assertThatThrownBy(() -> failingUseCase.applyVersion(KEY,
                new ApplyCommand(1, scheduled.docVersion(), true), ACTOR)).isSameAs(boom);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(reload(2).updatedBy()).isEqualTo(OTHER);

        assertThat(versionCount()).isEqualTo(2);
    }

    @Test
    void readsLegacyDocumentWithClassField() {
        mongo.getCollection(CONFIG).insertOne(new org.bson.Document("configKey", "instruction.legacy")
                .append("_class", "com.example.copilot.aiassistant.config.AssistantConfig")
                .append("configType", "SYSTEM_INSTRUCTION").append("name", "legacy").append("content", "body")
                .append("status", "ARCHIVED").append("version", 1).append("createdBy", "EEE11111")
                .append("createdAt", java.util.Date.from(NOW)).append("updatedBy", "EEE11111")
                .append("updatedAt", java.util.Date.from(NOW)).append("docVersion", 4L));

        AssistantConfig read = repository.findVersion(new ConfigKey("instruction.legacy"), 1).orElseThrow();

        assertThat(read.name()).isEqualTo("legacy");
        assertThat(read.configType()).isEqualTo(ConfigType.SYSTEM_INSTRUCTION);
        assertThat(read.status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(read.createdAt()).isEqualTo(NOW);
        assertThat(read.docVersion()).isEqualTo(4L);
    }

    @Test
    void findersMapEveryField() {
        AssistantConfig scheduled = scheduleDraft(NOW.plus(Duration.ofMinutes(2)));

        assertThat(repository.findPublished(KEY)).map(AssistantConfig::version).contains(1);
        assertThat(repository.findWorkingCopy(KEY)).contains(scheduled);
        assertThat(repository.findLatest(KEY)).contains(scheduled);
        assertThat(repository.findVersion(KEY, 2)).contains(scheduled);
        assertThat(repository.findVersion(KEY, 3)).isEmpty();
        assertThat(repository.findScheduledDue(NOW.plus(Duration.ofMinutes(1)))).isEmpty();
        assertThat(repository.findScheduledDue(NOW.plus(Duration.ofMinutes(2)))).containsExactly(scheduled);
        assertThat(repository.findPublished()).hasSize(8)
                .allSatisfy(c -> assertThat(c.status()).isEqualTo(ConfigStatus.PUBLISHED));
        assertThat(repository.count()).isEqualTo(9);
        assertThat(repository.findWorkingCopy(new ConfigKey("instruction.nope"))).isEmpty();
    }

    /** The stored document keeps its fields and carries no {@code _class}. */
    @Test
    void storedDocumentShapeIsUnchanged() {
        AssistantConfig draft = insertDraft(OTHER);

        Document raw = mongo.getCollection(CONFIG).find(Filters.and(Filters.eq("configKey", KEY.value()),
                Filters.eq("version", 2))).first();

        assertThat(raw).isNotNull();
        assertThat(raw.keySet()).containsExactly("_id", "configKey", "configType", "name", "content", "status",
                "version", "createdBy", "createdAt", "updatedBy", "updatedAt", "docVersion");
        assertThat(raw.getObjectId("_id").toHexString()).isEqualTo(draft.id());
        assertThat(raw).doesNotContainKey("_class");
        assertThat(raw.getString("status")).isEqualTo("DRAFT");
        assertThat(raw.get("docVersion")).isEqualTo(0L);
    }
}
