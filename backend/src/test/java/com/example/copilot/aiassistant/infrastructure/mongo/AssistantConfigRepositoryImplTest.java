package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.example.copilot.testsupport.LogCapture;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.TransactionSystemException;

/**
 * Transactions and conflict mapping of the config repository, against the real replica set. Spring Data failures
 * are injected through a delegating {@link AssistantConfigMongoRepository} handed to a hand-built implementation.
 */
class AssistantConfigRepositoryImplTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-20T02:00:00Z");
    private static final String KEY = "instruction.contact-prep";
    private static final String ACTOR = "EEE22222";
    private static final String OTHER = "EEE11111";
    private static final String CONFIG = "COLL_ICRM_AI_ASSISTANT_CONFIG";
    private static final String TOO_LONG_NAME = "名".repeat(51);

    @Autowired MongoClient mongoClient;
    @Autowired AssistantConfigMongoRepository mongoRepository;
    @Autowired MongoTemplate mongo;
    @Autowired MongoTransactionManager transactionManager;
    @Autowired SeedLoader seedLoader;

    private AssistantConfigRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new AssistantConfigRepositoryImpl(mongoRepository, mongo, transactionManager);
    }

    @AfterEach
    void restoreSeed() {
        mongoRepository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    private AssistantConfigRepositoryImpl withMongoRepository(AssistantConfigMongoRepository repo) {
        return new AssistantConfigRepositoryImpl(repo, mongo, transactionManager);
    }

    /** Delegates to the real Spring Data repository unless a call is stubbed. */
    private AssistantConfigMongoRepository delegatingMongoRepository() {
        return mock(AssistantConfigMongoRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(mongoRepository)));
    }

    private AssistantConfig reload(int version) {
        return repository.findVersion(KEY, version).orElseThrow();
    }

    private long countOf(ConfigStatus status) {
        return mongo.count(Query.query(where("configKey").is(KEY).and("status").is(status)), CONFIG);
    }

    private long versionCount() {
        return mongo.count(Query.query(where("configKey").is(KEY)), CONFIG);
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

        AssistantConfig published = repository.publish(
                draft.withStatus(ConfigStatus.PUBLISHED, null).touched(ACTOR, later));

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

    /** Moved from ConfigCommandServiceTest#publishedVersionChangedBeforeArchiveIsConflict. */
    @Test
    void publishedVersionChangedBeforeArchiveIsConflictAndRollsBack() {
        AssistantConfig v1Before = reload(1);
        AssistantConfig draft = insertDraft(OTHER);
        AssistantConfigMongoRepository racing = delegatingMongoRepository();
        doAnswer(inv -> {
            Optional<AssistantConfigDocument> read =
                    mongoRepository.findByConfigKeyAndStatus(inv.getArgument(0), inv.getArgument(1));
            read.ifPresent(d -> bumpDocVersion(d.getId()));
            return read;
        }).when(racing).findByConfigKeyAndStatus(any(), any());

        assertConflict(() -> withMongoRepository(racing).publish(
                draft.withStatus(ConfigStatus.PUBLISHED, null).touched(ACTOR, NOW)));

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
                    Filters.and(Filters.eq("configKey", KEY), Filters.eq("version", 1)),
                    Updates.set("changeNote", "uncommitted change by another transaction"));
            try {
                assertConflict(() -> repository.publish(draft.withStatus(ConfigStatus.PUBLISHED, null)));
            } finally {
                other.abortTransaction();
            }
        }

        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());
    }

    @Test
    void publishScheduledLosesRaceReturnsFalseAndRollsBackArchive() {
        AssistantConfig v1Before = reload(1);
        Instant at = NOW.plus(Duration.ofMinutes(2));
        scheduleDraft(at);
        Instant due = NOW.plus(Duration.ofMinutes(3));
        AssistantConfig stale = repository.findScheduledDue(due).getFirst();

        // Someone saves the scheduled version between the scheduler's query and its conditional claim.
        repository.save(stale.withContent("saved meanwhile", stale.content(), "note").touched(OTHER, due));

        assertThat(repository.publishScheduled(stale, due)).isFalse();
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(1).docVersion()).isEqualTo(v1Before.docVersion());
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);

        // A fresh read claims it: published at the run time, the last editor stays the updatedBy.
        AssistantConfig fresh = repository.findScheduledDue(due).getFirst();
        assertThat(repository.publishScheduled(fresh, due)).isTrue();
        AssistantConfig v2 = reload(2);
        assertThat(v2.status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(v2.updatedBy()).isEqualTo(OTHER);
        assertThat(v2.updatedAt()).isEqualTo(due);
        assertThat(v2.scheduledAt()).isEqualTo(at);
        assertThat(v2.docVersion()).isEqualTo(fresh.docVersion() + 1);
        AssistantConfig v1 = reload(1);
        assertThat(v1.status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.updatedAt()).isEqualTo(v1Before.updatedAt());
        assertThat(repository.findScheduledDue(due)).isEmpty();
        assertThat(repository.publishScheduled(fresh, due)).isFalse();
        assertThat(countOf(ConfigStatus.PUBLISHED)).isEqualTo(1);
    }

    /** Impl-level half of ScheduledPublishJobTest#writeConflictOnClaimIsLoggedAtDebug. */
    @Test
    void publishScheduledWriteConflictIsLostRaceLoggedAtDebug() {
        scheduleDraft(NOW.plus(Duration.ofMinutes(2)));
        Instant due = NOW.plus(Duration.ofMinutes(3));
        AssistantConfig scheduled = repository.findScheduledDue(due).getFirst();
        AssistantConfigMongoRepository failing = delegatingMongoRepository();
        doThrow(new DataIntegrityViolationException("x", new MongoException(112, "conflict")))
                .when(failing).findByConfigKeyAndStatus(any(), any());

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(AssistantConfigRepositoryImpl.class, Level.DEBUG)) {
            assertThat(withMongoRepository(failing).publishScheduled(scheduled, due)).isFalse();
            events = capture.events();
        }

        assertThat(events).extracting(LogEvent::getLevel).containsExactly(Level.DEBUG);
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
        AssistantConfigMongoRepository failing = delegatingMongoRepository();
        doThrow(failure).when(failing).findByConfigKeyAndStatus(any(), any());

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(AssistantConfigRepositoryImpl.class, Level.DEBUG)) {
            assertThatThrownBy(() -> withMongoRepository(failing).publishScheduled(scheduled, due)).isSameAs(failure);
            events = capture.events();
        }

        assertThat(events).isEmpty();
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
    }

    /** Moved from ScheduledPublishJobTest#lostRaceClassification (was ConfigCommandService.isLostRace). */
    @Test
    void lostRaceClassification() {
        assertThat(AssistantConfigRepositoryImpl.isLostRace(new AssistantException(ErrorCode.AI_CONFIG_CONFLICT)))
                .isTrue();
        assertThat(AssistantConfigRepositoryImpl.isLostRace(new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND)))
                .isFalse();
        assertThat(AssistantConfigRepositoryImpl.isLostRace(new RuntimeException(new MongoException(112, "w"))))
                .isTrue();
        assertThat(AssistantConfigRepositoryImpl.isLostRace(new RuntimeException(new MongoException(11000, "d"))))
                .isTrue();
        assertThat(AssistantConfigRepositoryImpl.isLostRace(new RuntimeException(
                labelled(91, MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)))).isFalse();
        assertThat(AssistantConfigRepositoryImpl.isLostRace(new RuntimeException(
                labelled(1, MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)))).isFalse();
        assertThat(AssistantConfigRepositoryImpl.isLostRace(new RuntimeException("other"))).isFalse();
    }

    @Test
    void validationFailure121Propagates() {
        AssistantConfig draft = insertDraft(OTHER);

        // Outside a transaction (insert, save) ...
        assertValidationFailure(() -> repository.insert(
                AssistantConfig.newDraft("instruction.too-long", ConfigType.SYSTEM_INSTRUCTION, TOO_LONG_NAME,
                        "x", 1, ACTOR, NOW)));
        assertValidationFailure(() -> repository.save(draft.withContent(TOO_LONG_NAME, "x", null)));
        // ... and inside the publishing and applying transactions.
        assertValidationFailure(() -> repository.publish(
                draft.withContent(TOO_LONG_NAME, draft.content(), null).withStatus(ConfigStatus.PUBLISHED, null)));
        assertValidationFailure(() -> repository.replaceWorkingCopy(
                draft.withStatus(ConfigStatus.DISCARDED, null),
                AssistantConfig.newDraft(KEY, ConfigType.SYSTEM_INSTRUCTION, TOO_LONG_NAME, "x", 3, ACTOR, NOW)));

        assertThat(repository.findLatest("instruction.too-long")).isEmpty();
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

    /** Moved from ConfigCommandServiceTest#applyIsAtomic. */
    @Test
    void replaceWorkingCopyFailureRollsBackDiscard() {
        AssistantConfigMongoRepository failing = delegatingMongoRepository();
        DataAccessResourceFailureException boom = new DataAccessResourceFailureException("insert failed");
        doThrow(boom).when(failing).insert(any(AssistantConfigDocument.class));
        doThrow(boom).when(failing).save(argThat((AssistantConfigDocument d) -> d != null && d.getId() == null));
        AssistantConfigRepositoryImpl failingRepository = withMongoRepository(failing);

        AssistantConfig draft = insertDraft(OTHER);
        assertThatThrownBy(() -> failingRepository.replaceWorkingCopy(
                draft.withStatus(ConfigStatus.DISCARDED, null).touched(ACTOR, NOW),
                newDraft(3, ACTOR).withBasedOnVersion(1))).isSameAs(boom);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());

        AssistantConfig scheduled = repository.save(
                draft.withStatus(ConfigStatus.SCHEDULED, NOW.plus(Duration.ofHours(1))).touched(OTHER, NOW));
        assertThatThrownBy(() -> failingRepository.replaceWorkingCopy(
                scheduled.withStatus(ConfigStatus.DISCARDED, scheduled.scheduledAt()).touched(ACTOR, NOW),
                newDraft(3, ACTOR).withBasedOnVersion(1))).isSameAs(boom);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(reload(2).updatedBy()).isEqualTo(OTHER);

        assertThat(versionCount()).isEqualTo(2);
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
        assertThat(repository.existsByConfigType(ConfigType.SYSTEM_PROMPT)).isTrue();
        assertThat(repository.count()).isEqualTo(9);
        assertThat(repository.findWorkingCopy("instruction.nope")).isEmpty();
    }

    /** The stored document keeps the fields and the pre-refactor {@code _class} class name. */
    @Test
    void storedDocumentShapeIsUnchanged() {
        AssistantConfig draft = insertDraft(OTHER);

        Document raw = mongo.getCollection(CONFIG).find(Filters.and(Filters.eq("configKey", KEY),
                Filters.eq("version", 2))).first();

        assertThat(raw).isNotNull();
        assertThat(raw.keySet()).containsExactly("_id", "configKey", "configType", "name", "content", "status",
                "version", "createdBy", "createdAt", "updatedBy", "updatedAt", "docVersion", "_class");
        assertThat(raw.getObjectId("_id").toHexString()).isEqualTo(draft.id());
        assertThat(raw.getString("_class")).isEqualTo("com.example.copilot.aiassistant.config.AssistantConfig");
        assertThat(raw.getString("status")).isEqualTo("DRAFT");
        assertThat(raw.get("docVersion")).isEqualTo(0L);
    }
}
