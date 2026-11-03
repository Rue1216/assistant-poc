package com.example.copilot.aiassistant.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.config.ValidationError;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import com.example.copilot.aiassistant.domain.exception.WorkingCopyDetail;
import com.example.copilot.aiassistant.domain.prompt.ExampleQuestionParser;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/** Each test maps to a row of the state-transition table in 04 section 2.4. */
class ConfigCommandServiceTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-20T02:00:00Z");
    private static final String KEY = "instruction.contact-prep";
    private static final String ACTOR = "EEE22222";
    private static final String OTHER = "EEE11111";
    private static final String INVALID_CONTENT = "## 其他\n只有一個章節";
    private static final String CONFIG = "COLL_ICRM_AI_ASSISTANT_CONFIG";

    @Autowired MongoClient mongoClient;
    @Autowired AssistantConfigRepository repository;
    @Autowired MongoTemplate mongo;
    @Autowired ConfigValidator validator;
    @Autowired AiAssistantProperties properties;
    @Autowired SeedLoader seedLoader;

    private MutableClock clock;
    private PublishedConfigCache cache;
    private ConfigCommandService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        cache = mock(PublishedConfigCache.class);
        service = newService(repository);
    }

    @AfterEach
    void restoreSeed() {
        mongo.remove(new Query(), CONFIG);
        seedLoader.run(new DefaultApplicationArguments());
    }

    private ConfigCommandService newService(AssistantConfigRepository repo) {
        return new ConfigCommandService(repo, validator, cache, clock, properties);
    }

    private AssistantConfig reload(int version) {
        return repository.findVersion(KEY, version).orElseThrow();
    }

    private long versionCount() {
        return mongo.count(Query.query(where("configKey").is(KEY)), CONFIG);
    }

    private static AssistantException assertError(ThrowingCallable call, ErrorCode code) {
        AssistantException[] caught = new AssistantException[1];
        assertThatThrownBy(call).isInstanceOfSatisfying(AssistantException.class, e -> {
            assertThat(e.code()).isEqualTo(code);
            caught[0] = e;
        });
        return caught[0];
    }

    private String validContent(String extraNote) {
        return reload(1).content() + "\n- " + extraNote + "\n";
    }

    private AssistantConfig save(AssistantConfig current, String name, String content, SaveMode mode, String actor) {
        return service.saveWorkingCopy(KEY, new SaveCommand(name, content, "note", mode, current.docVersion()), actor);
    }

    private AssistantConfig schedule(AssistantConfig current, Instant at, String actor) {
        return service.publish(KEY, new PublishCommand(PublishMode.SCHEDULED, at, current.docVersion()), actor);
    }

    private AssistantConfig publishNow(AssistantConfig current, String actor) {
        return service.publish(KEY, new PublishCommand(PublishMode.IMMEDIATE, null, current.docVersion()), actor);
    }

    /** v1 ARCHIVED, v2 PUBLISHED (renamed, extra note), no working copy. */
    private AssistantConfig publishV2() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        draft = save(draft, "聯繫準備 v2", validContent("v2 補充"), SaveMode.KEEP_STATUS, ACTOR);
        AssistantConfig v2 = publishNow(draft, ACTOR);
        reset(cache);
        return v2;
    }

    @Test
    void createDraftCopiesPublishedAndIncrementsVersion() {
        AssistantConfig v1 = reload(1);

        AssistantConfig draft = service.createDraft(KEY, ACTOR);

        assertThat(draft.version()).isEqualTo(2);
        assertThat(draft.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(draft.name()).isEqualTo(v1.name());
        assertThat(draft.content()).isEqualTo(v1.content());
        assertThat(draft.configType()).isEqualTo(ConfigType.SYSTEM_INSTRUCTION);
        assertThat(draft.basedOnVersion()).isEqualTo(1);
        assertThat(draft.changeNote()).isNull();
        assertThat(draft.scheduledAt()).isNull();
        assertThat(draft.createdBy()).isEqualTo(ACTOR);
        assertThat(draft.createdAt()).isEqualTo(NOW);
        assertThat(draft.updatedBy()).isEqualTo(ACTOR);
        assertThat(draft.updatedAt()).isEqualTo(NOW);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
    }

    @Test
    void createDraftFailsWhenWorkingCopyExists() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        assertError(() -> service.createDraft(KEY, ACTOR), ErrorCode.AI_CONFIG_INVALID_STATE);

        schedule(draft, NOW.plus(Duration.ofHours(1)), ACTOR);
        assertError(() -> service.createDraft(KEY, ACTOR), ErrorCode.AI_CONFIG_INVALID_STATE);

        assertThat(versionCount()).isEqualTo(2);
    }

    @Test
    void createDraftRecordsSourceVersion() {
        AssistantConfig first = service.createDraft(KEY, ACTOR);
        assertThat(first.basedOnVersion()).isEqualTo(1);
        service.discardDraft(KEY, first.docVersion(), ACTOR);

        AssistantConfig published = publishV2();
        assertThat(service.createDraft(KEY, ACTOR).basedOnVersion()).isEqualTo(published.version());
    }

    @Test
    void createDraftWithoutPublishedUsesLatestAsSource() {
        String key = "instruction.new-topic";
        AssistantConfig v1 = service.createKey(key, ConfigType.SYSTEM_INSTRUCTION, "新分類", ACTOR);
        service.discardDraft(key, v1.docVersion(), ACTOR);

        AssistantConfig draft = service.createDraft(key, ACTOR);

        assertThat(draft.version()).isEqualTo(2);
        assertThat(draft.basedOnVersion()).isEqualTo(1);
    }

    @Test
    void createDraftForUnknownKeyIsNotFound() {
        assertError(() -> service.createDraft("instruction.nope", ACTOR), ErrorCode.AI_CONFIG_NOT_FOUND);
    }

    @Test
    void saveKeepStatusOnDraftStaysDraft() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        clock.advance(Duration.ofMinutes(5));

        // A DRAFT may hold content that would not pass the pre-publish check.
        AssistantConfig saved = save(draft, "新名稱", INVALID_CONTENT, SaveMode.KEEP_STATUS, ACTOR);

        assertThat(saved.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(saved.name()).isEqualTo("新名稱");
        assertThat(saved.content()).isEqualTo(INVALID_CONTENT);
        assertThat(saved.changeNote()).isEqualTo("note");
        assertThat(saved.updatedBy()).isEqualTo(ACTOR);
        assertThat(saved.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(saved.createdBy()).isEqualTo(OTHER);
        assertThat(saved.docVersion()).isEqualTo(draft.docVersion() + 1);
        assertThat(reload(2).content()).isEqualTo(INVALID_CONTENT);

        AssistantConfig asDraft = save(saved, "新名稱", "x", SaveMode.DRAFT, ACTOR);
        assertThat(asDraft.status()).isEqualTo(ConfigStatus.DRAFT);
    }

    @Test
    void saveKeepStatusOnScheduledKeepsScheduleAndRevalidates() {
        Instant at = NOW.plus(Duration.ofHours(1));
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), at, OTHER);
        clock.advance(Duration.ofMinutes(1));

        String content = validContent("排程中修改");
        AssistantConfig saved = save(scheduled, "聯繫準備", content, SaveMode.KEEP_STATUS, ACTOR);

        assertThat(saved.status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(saved.scheduledAt()).isEqualTo(at);
        assertThat(saved.content()).isEqualTo(content);
        assertThat(saved.updatedBy()).isEqualTo(ACTOR);
        assertThat(saved.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));

        AssistantException e = assertError(
                () -> save(saved, "聯繫準備", INVALID_CONTENT, SaveMode.KEEP_STATUS, OTHER),
                ErrorCode.AI_CONFIG_VALIDATION_FAILED);
        assertThat(e.details()).isNotEmpty().allSatisfy(d -> assertThat(d).isInstanceOf(ValidationError.class));

        AssistantConfig after = reload(2);
        assertThat(after.content()).isEqualTo(content);
        assertThat(after.status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(after.updatedBy()).isEqualTo(ACTOR);
        assertThat(after.docVersion()).isEqualTo(saved.docVersion());
    }

    private AssistantConfig staleDraftWithForbiddenChangeNote() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        return repository.save(draft.withContent(draft.name(), draft.content(), "0912-345-678"));
    }

    @Test
    void saveDraftRejectsForbiddenNameOrChangeNote() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);

        AssistantException e = assertError(() -> service.saveWorkingCopy(KEY,
                new SaveCommand("聯繫準備", draft.content(), "0912-345-678", SaveMode.DRAFT, draft.docVersion()), ACTOR),
                ErrorCode.AI_CONFIG_VALIDATION_FAILED);

        assertThat(e.details()).extracting(d -> ((ValidationError) d).message())
                .contains("修改說明疑似包含手機號碼，請移除");
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());
    }

    @Test
    void saveDraftStillAllowsForbiddenContent() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);

        AssistantConfig saved = save(draft, "聯繫準備", validContent("內網 10.1.2.3"), SaveMode.DRAFT, ACTOR);

        assertThat(saved.content()).contains("10.1.2.3");
    }

    @Test
    void staleForbiddenChangeNoteCanBeFixedInTheSameSave() {
        AssistantConfig stale = staleDraftWithForbiddenChangeNote();

        AssistantConfig saved = service.saveWorkingCopy(KEY,
                new SaveCommand("聯繫準備", stale.content(), "乾淨的說明", SaveMode.DRAFT, stale.docVersion()), ACTOR);

        assertThat(saved.changeNote()).isEqualTo("乾淨的說明");
    }

    @Test
    void publishRejectsForbiddenChangeNote() {
        AssistantConfig stale = staleDraftWithForbiddenChangeNote();

        AssistantException e = assertError(() -> publishNow(stale, ACTOR), ErrorCode.AI_CONFIG_VALIDATION_FAILED);

        assertThat(e.details()).extracting(d -> ((ValidationError) d).message())
                .contains("修改說明疑似包含手機號碼，請移除");
    }

    @Test
    void saveAsDraftCancelsSchedule() {
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), NOW.plus(Duration.ofHours(1)), OTHER);

        AssistantConfig saved = save(scheduled, "聯繫準備", INVALID_CONTENT, SaveMode.DRAFT, ACTOR);

        assertThat(saved.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(saved.scheduledAt()).isNull();
        assertThat(saved.updatedBy()).isEqualTo(ACTOR);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).scheduledAt()).isNull();
    }

    @Test
    void staleDocVersionConflicts() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        AssistantConfig saved = save(draft, "聯繫準備", validContent("a"), SaveMode.KEEP_STATUS, ACTOR);

        assertError(() -> save(draft, "聯繫準備", "stale", SaveMode.KEEP_STATUS, OTHER),
                ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> publishNow(draft, OTHER), ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> schedule(draft, NOW.plus(Duration.ofHours(1)), OTHER), ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> service.discardDraft(KEY, draft.docVersion(), OTHER), ErrorCode.AI_CONFIG_CONFLICT);

        AssistantConfig after = reload(2);
        assertThat(after.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(after.docVersion()).isEqualTo(saved.docVersion());
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
    }

    /** A delegating repository whose working-copy / scheduled reads can be intercepted. */
    private AssistantConfigRepository delegatingRepository() {
        return mock(AssistantConfigRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
    }

    private void bumpDocVersion(AssistantConfig c) {
        mongo.updateFirst(Query.query(where("_id").is(new ObjectId(c.id()))), new Update().inc("docVersion", 1L),
                CONFIG);
    }

    @Test
    void transactionWriteConflictOnPublishedVersionIsConflict() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);

        try (ClientSession other = mongoClient.startSession()) {
            other.startTransaction();
            mongo.getCollection(CONFIG).updateOne(other,
                    Filters.and(Filters.eq("configKey", KEY), Filters.eq("version", 1)),
                    Updates.set("changeNote", "uncommitted change by another transaction"));
            try {
                assertError(() -> publishNow(draft, OTHER), ErrorCode.AI_CONFIG_CONFLICT);
            } finally {
                other.abortTransaction();
            }
        }

        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());
        verify(cache, never()).invalidate();
    }

    @Test
    void docVersionChangedBetweenReadAndSaveIsConflict() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        AssistantConfigRepository racing = delegatingRepository();
        doAnswer(inv -> {
            Optional<AssistantConfig> read = repository.findWorkingCopy(inv.getArgument(0));
            bumpDocVersion(draft);
            return read;
        }).when(racing).findWorkingCopy(any());

        assertError(() -> newService(racing).saveWorkingCopy(KEY,
                new SaveCommand("聯繫準備", "競爭中的儲存", null, SaveMode.KEEP_STATUS, draft.docVersion()), ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);

        assertThat(reload(2).content()).isEqualTo(draft.content());
        assertThat(reload(2).updatedBy()).isEqualTo(OTHER);
    }

    @Test
    void documentValidationFailureIsNotAConflict() {
        String tooLong = "名".repeat(51);
        AssistantConfig draft = service.createDraft(KEY, OTHER);

        // Outside a transaction (save) ...
        assertThatThrownBy(() -> save(draft, tooLong, "x", SaveMode.KEEP_STATUS, ACTOR))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().isInstanceOfSatisfying(MongoException.class, e -> assertThat(e.getCode()).isEqualTo(121));

        // ... and inside the publishing transaction.
        AssistantConfigRepository corrupting = delegatingRepository();
        doAnswer(inv -> repository.findWorkingCopy(inv.getArgument(0))
                .map(c -> c.withContent(tooLong, c.content(), c.changeNote())))
                .when(corrupting).findWorkingCopy(any());
        assertThatThrownBy(() -> newService(corrupting).publish(KEY,
                new PublishCommand(PublishMode.IMMEDIATE, null, draft.docVersion()), ACTOR))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().isInstanceOfSatisfying(MongoException.class, e -> assertThat(e.getCode()).isEqualTo(121));

        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
    }

    @Test
    void operationsWithoutWorkingCopyOnExistingKeyAreConflicts() {
        assertError(() -> service.saveWorkingCopy(KEY,
                new SaveCommand("n", "c", null, SaveMode.KEEP_STATUS, 0), ACTOR), ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> service.publish(KEY, new PublishCommand(PublishMode.IMMEDIATE, null, 0), ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> service.discardDraft(KEY, 0, ACTOR), ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> service.validateWorkingCopy(KEY), ErrorCode.AI_CONFIG_CONFLICT);
    }

    @Test
    void operationsOnUnknownKeyAreNotFound() {
        String unknown = "instruction.nope";
        assertError(() -> service.saveWorkingCopy(unknown,
                new SaveCommand("n", "c", null, SaveMode.KEEP_STATUS, 0), ACTOR), ErrorCode.AI_CONFIG_NOT_FOUND);
        assertError(() -> service.publish(unknown, new PublishCommand(PublishMode.IMMEDIATE, null, 0), ACTOR),
                ErrorCode.AI_CONFIG_NOT_FOUND);
        assertError(() -> service.discardDraft(unknown, 0, ACTOR), ErrorCode.AI_CONFIG_NOT_FOUND);
        assertError(() -> service.validateWorkingCopy(unknown), ErrorCode.AI_CONFIG_NOT_FOUND);
    }

    @Test
    void publishImmediateArchivesPrevious() {
        AssistantConfig v1Before = reload(1);
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        clock.advance(Duration.ofMinutes(10));

        AssistantConfig published = publishNow(draft, ACTOR);

        assertThat(published.version()).isEqualTo(2);
        assertThat(published.status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(published.updatedBy()).isEqualTo(ACTOR);
        assertThat(published.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.PUBLISHED);

        AssistantConfig v1 = reload(1);
        assertThat(v1.status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.updatedBy()).isEqualTo(v1Before.updatedBy());
        assertThat(v1.updatedAt()).isEqualTo(v1Before.updatedAt());
        assertThat(v1.content()).isEqualTo(v1Before.content());

        assertThat(mongo.count(Query.query(where("configKey").is(KEY).and("status").is(ConfigStatus.PUBLISHED)),
                CONFIG)).isEqualTo(1);
        verify(cache).invalidate();
    }

    @Test
    void publishInvalidContentFails() {
        AssistantConfig draft = save(service.createDraft(KEY, ACTOR), "聯繫準備", INVALID_CONTENT,
                SaveMode.KEEP_STATUS, ACTOR);

        AssistantException now = assertError(() -> publishNow(draft, ACTOR), ErrorCode.AI_CONFIG_VALIDATION_FAILED);
        assertThat(now.details()).extracting(d -> ((ValidationError) d).rule()).contains("REQUIRED_SECTION");
        assertError(() -> schedule(draft, NOW.plus(Duration.ofHours(1)), ACTOR),
                ErrorCode.AI_CONFIG_VALIDATION_FAILED);

        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        verify(cache, never()).invalidate();
    }

    @Test
    void scheduleSetsScheduledAt() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        Instant at = NOW.plus(Duration.ofMinutes(2));

        AssistantConfig scheduled = schedule(draft, at, ACTOR);

        assertThat(scheduled.status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(scheduled.scheduledAt()).isEqualTo(at);
        assertThat(scheduled.updatedBy()).isEqualTo(ACTOR);
        assertThat(scheduled.updatedAt()).isEqualTo(NOW);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        verify(cache, never()).invalidate();
    }

    @Test
    void scheduleOutsideWindowIsInputInvalid() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);

        for (Instant at : new Instant[] {NOW.plusSeconds(30), NOW.plus(Duration.ofDays(91)), null}) {
            AssistantException e = assertError(() -> schedule(draft, at, ACTOR), ErrorCode.AI_INPUT_INVALID);
            assertThat(e.details()).containsExactly(new FieldDetail("scheduledAt"));
        }
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);

        // Both bounds are inclusive.
        AssistantConfig atMin = schedule(draft, NOW.plus(Duration.ofMinutes(1)), ACTOR);
        AssistantConfig atMax = schedule(atMin, NOW.plus(Duration.ofDays(90)), ACTOR);
        assertThat(atMax.scheduledAt()).isEqualTo(NOW.plus(Duration.ofDays(90)));
    }

    @Test
    void rescheduleChangesTime() {
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), NOW.plus(Duration.ofHours(1)), OTHER);
        clock.advance(Duration.ofMinutes(3));

        AssistantConfig moved = schedule(scheduled, NOW.plus(Duration.ofHours(2)), ACTOR);

        assertThat(moved.status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(moved.scheduledAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(moved.updatedBy()).isEqualTo(ACTOR);
        assertThat(moved.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3)));
        assertThat(reload(2).scheduledAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
    }

    @Test
    void publishImmediateOnScheduledPublishesNow() {
        AssistantConfig v1Before = reload(1);
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), NOW.plus(Duration.ofHours(1)), OTHER);
        clock.advance(Duration.ofMinutes(5));

        AssistantConfig published = publishNow(scheduled, ACTOR);

        assertThat(published.status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(published.updatedBy()).isEqualTo(ACTOR);
        assertThat(published.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(published.scheduledAt()).isNull();
        assertThat(reload(published.version()).scheduledAt()).isNull();
        AssistantConfig v1 = reload(1);
        assertThat(v1.status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.updatedAt()).isEqualTo(v1Before.updatedAt());
        verify(cache).invalidate();

        // Nothing is left for the scheduler.
        clock.advance(Duration.ofHours(2));
        assertThat(service.publishDue()).isZero();
    }

    @Test
    void publishDuePublishesAndKeepsScheduler() {
        AssistantConfig v1Before = reload(1);
        Instant at = NOW.plus(Duration.ofHours(1));
        schedule(service.createDraft(KEY, ACTOR), at, OTHER);

        clock.advance(Duration.ofMinutes(59));
        assertThat(service.publishDue()).isZero();
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
        verify(cache, never()).invalidate();

        clock.advance(Duration.ofMinutes(1).plusSeconds(20));
        assertThat(service.publishDue()).isEqualTo(1);

        AssistantConfig v2 = reload(2);
        assertThat(v2.status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(v2.updatedBy()).isEqualTo(OTHER);
        assertThat(v2.updatedAt()).isEqualTo(at.plusSeconds(20));
        assertThat(v2.scheduledAt()).isEqualTo(at);
        AssistantConfig v1 = reload(1);
        assertThat(v1.status()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.updatedBy()).isEqualTo(v1Before.updatedBy());
        assertThat(v1.updatedAt()).isEqualTo(v1Before.updatedAt());
        verify(cache).invalidate();

        assertThat(service.publishDue()).isZero();
    }

    @Test
    void publishDueSkipsVersionChangedAfterItWasRead() {
        AssistantConfig scheduled = schedule(service.createDraft(KEY, ACTOR), NOW.plus(Duration.ofMinutes(2)), OTHER);
        clock.advance(Duration.ofMinutes(3));

        // Someone saves the scheduled version between the scheduler's query and its conditional update.
        AssistantConfigRepository racing = mock(AssistantConfigRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
        doAnswer(inv -> {
            List<AssistantConfig> stale = repository.findScheduledDue(inv.getArgument(0));
            save(scheduled, "聯繫準備", validContent("搶先儲存"), SaveMode.KEEP_STATUS, ACTOR);
            return stale;
        }).when(racing).findScheduledDue(any());

        assertThat(newService(racing).publishDue()).isZero();

        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        verify(cache, never()).invalidate();
    }

    @Test
    void publishDueRevertsInvalidScheduledVersion() {
        AssistantConfig scheduled = schedule(service.createDraft(KEY, ACTOR), NOW.plus(Duration.ofMinutes(2)), OTHER);
        repository.save(scheduled.withContent(scheduled.name(), INVALID_CONTENT, "note"));
        clock.advance(Duration.ofMinutes(3));

        assertThat(service.publishDue()).isZero();

        AssistantConfig v2 = reload(2);
        assertThat(v2.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(v2.scheduledAt()).isNull();
        assertThat(v2.updatedBy()).isEqualTo(AssistantConfig.SYSTEM_ACTOR);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        verify(cache, never()).invalidate();
    }

    @Test
    void publishDueRevertsWhenRulesChangedAfterScheduling() {
        ConfigCommandService permissive = new ConfigCommandService(repository,
                new ConfigValidator(new ExampleQuestionParser(), List.of("docs.example.com")), cache, clock,
                properties);
        ConfigCommandService strict = new ConfigCommandService(repository,
                new ConfigValidator(new ExampleQuestionParser(), List.of()), cache, clock, properties);
        AssistantConfig draft = permissive.createDraft(KEY, ACTOR);
        draft = permissive.saveWorkingCopy(KEY, new SaveCommand(draft.name(),
                draft.content() + "\n- 參考 https://docs.example.com/x\n", "note", SaveMode.KEEP_STATUS,
                draft.docVersion()), ACTOR);
        permissive.publish(KEY, new PublishCommand(PublishMode.SCHEDULED, NOW.plus(Duration.ofMinutes(2)),
                draft.docVersion()), OTHER);
        clock.advance(Duration.ofMinutes(3));

        assertThat(strict.publishDue()).isZero();

        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
    }

    @Test
    void publishDuePublishesValidOnesInTheSameRun() {
        String otherKey = "instruction.market-brief";
        AssistantConfig otherDraft = service.createDraft(otherKey, ACTOR);
        AssistantConfig other = service.publish(otherKey, new PublishCommand(PublishMode.SCHEDULED,
                NOW.plus(Duration.ofMinutes(2)), otherDraft.docVersion()), OTHER);
        AssistantConfig scheduled = schedule(service.createDraft(KEY, ACTOR), NOW.plus(Duration.ofMinutes(2)), OTHER);
        repository.save(scheduled.withContent(scheduled.name(), INVALID_CONTENT, "note"));
        clock.advance(Duration.ofMinutes(3));

        assertThat(service.publishDue()).isEqualTo(1);

        assertThat(repository.findVersion(otherKey, other.version()).orElseThrow().status())
                .isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(service.findWorkingCopy(KEY).orElseThrow().status()).isEqualTo(ConfigStatus.DRAFT);
        verify(cache).invalidate();
    }

    @Test
    void saveAfterRevertWithStaleDocVersionConflicts() {
        AssistantConfig scheduled = schedule(service.createDraft(KEY, ACTOR), NOW.plus(Duration.ofMinutes(2)), OTHER);
        repository.save(scheduled.withContent(scheduled.name(), INVALID_CONTENT, "note"));
        AssistantConfig preRevert = reload(2);
        clock.advance(Duration.ofMinutes(3));
        service.publishDue();

        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(preRevert.docVersion() + 1);
        assertError(() -> save(preRevert, "聯繫準備", validContent("過期"), SaveMode.KEEP_STATUS, ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);
    }

    @Test
    void discardOnlyAllowedForDraft() {
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), NOW.plus(Duration.ofHours(1)), OTHER);

        assertError(() -> service.discardDraft(KEY, scheduled.docVersion(), ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);

        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
    }

    @Test
    void discardDraftIsSoft() {
        AssistantConfig draft = save(service.createDraft(KEY, OTHER), "聯繫準備", "未完成的草稿",
                SaveMode.KEEP_STATUS, OTHER);
        clock.advance(Duration.ofMinutes(7));

        service.discardDraft(KEY, draft.docVersion(), ACTOR);

        AssistantConfig discarded = reload(2);
        assertThat(discarded.status()).isEqualTo(ConfigStatus.DISCARDED);
        assertThat(discarded.content()).isEqualTo("未完成的草稿");
        assertThat(discarded.createdBy()).isEqualTo(OTHER);
        assertThat(discarded.updatedBy()).isEqualTo(ACTOR);
        assertThat(discarded.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(7)));

        AssistantConfig next = service.createDraft(KEY, ACTOR);
        assertThat(next.version()).isEqualTo(3);
        assertThat(next.content()).isEqualTo(reload(1).content());
    }

    @Test
    void applyWithoutWorkingCopyCreatesDraft() {
        AssistantConfig v1 = reload(1);
        AssistantConfig v2 = publishV2();
        clock.advance(Duration.ofMinutes(30));

        AssistantConfig applied = service.applyVersion(KEY, new ApplyCommand(1, null, false), OTHER);

        assertThat(applied.version()).isEqualTo(3);
        assertThat(applied.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(applied.name()).isEqualTo(v1.name());
        assertThat(applied.content()).isEqualTo(v1.content());
        assertThat(applied.basedOnVersion()).isEqualTo(1);
        assertThat(applied.changeNote()).isNull();
        assertThat(applied.scheduledAt()).isNull();
        assertThat(applied.createdBy()).isEqualTo(OTHER);
        assertThat(applied.updatedBy()).isEqualTo(OTHER);
        assertThat(applied.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));

        assertThat(reload(2).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).docVersion()).isEqualTo(v2.docVersion());
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.ARCHIVED);
        verify(cache, never()).invalidate();
    }

    @Test
    void applyOverDraftRequiresConfirmationAndDiscardsIt() {
        AssistantConfig v1 = reload(1);
        publishV2();
        AssistantConfig draft = save(service.createDraft(KEY, OTHER), "聯繫準備", "草稿內容",
                SaveMode.KEEP_STATUS, OTHER);

        for (Long seen : new Long[] {null, draft.docVersion()}) {
            AssistantException e = assertError(
                    () -> service.applyVersion(KEY, new ApplyCommand(1, seen, false), ACTOR),
                    ErrorCode.AI_CONFIG_OVERWRITE_PENDING);
            assertThat(e.details()).hasSize(1);
            WorkingCopyDetail pending = (WorkingCopyDetail) e.details().get(0);
            assertThat(pending.status()).isEqualTo(ConfigStatus.DRAFT);
            assertThat(pending.scheduledAt()).isNull();
            assertThat(pending.updatedAt()).isEqualTo(NOW);
            assertThat(pending.docVersion()).isEqualTo(draft.docVersion());
        }
        assertThat(reload(3).status()).isEqualTo(ConfigStatus.DRAFT);

        clock.advance(Duration.ofMinutes(4));
        AssistantConfig applied = service.applyVersion(KEY, new ApplyCommand(1, draft.docVersion(), true), ACTOR);

        assertThat(applied.version()).isEqualTo(4);
        assertThat(applied.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(applied.content()).isEqualTo(v1.content());
        assertThat(applied.basedOnVersion()).isEqualTo(1);

        AssistantConfig replaced = reload(3);
        assertThat(replaced.status()).isEqualTo(ConfigStatus.DISCARDED);
        assertThat(replaced.content()).isEqualTo("草稿內容");
        assertThat(replaced.createdBy()).isEqualTo(OTHER);
        assertThat(replaced.updatedBy()).isEqualTo(ACTOR);
        assertThat(replaced.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.PUBLISHED);
        verify(cache, never()).invalidate();
    }

    @Test
    void applyOverScheduledDiscardsItAndCancelsSchedule() {
        Instant at = NOW.plus(Duration.ofHours(1));
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), at, OTHER);

        AssistantException e = assertError(
                () -> service.applyVersion(KEY, new ApplyCommand(1, scheduled.docVersion(), false), ACTOR),
                ErrorCode.AI_CONFIG_OVERWRITE_PENDING);
        WorkingCopyDetail pending = (WorkingCopyDetail) e.details().get(0);
        assertThat(pending.status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(pending.scheduledAt()).isEqualTo(at);

        AssistantConfig applied = service.applyVersion(KEY, new ApplyCommand(1, scheduled.docVersion(), true), ACTOR);

        assertThat(applied.version()).isEqualTo(3);
        assertThat(applied.status()).isEqualTo(ConfigStatus.DRAFT);
        AssistantConfig replaced = reload(2);
        assertThat(replaced.status()).isEqualTo(ConfigStatus.DISCARDED);
        assertThat(replaced.scheduledAt()).isEqualTo(at);
        assertThat(replaced.updatedBy()).isEqualTo(ACTOR);

        clock.advance(Duration.ofHours(2));
        assertThat(service.publishDue()).isZero();
        assertThat(reload(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DISCARDED);
        assertThat(reload(3).status()).isEqualTo(ConfigStatus.DRAFT);
    }

    @Test
    void applyFromDiscardedRestoresContent() {
        AssistantConfig draft = save(service.createDraft(KEY, OTHER), "捨棄前的名稱", "捨棄前的內容",
                SaveMode.KEEP_STATUS, OTHER);
        service.discardDraft(KEY, draft.docVersion(), OTHER);

        AssistantConfig applied = service.applyVersion(KEY, new ApplyCommand(2, null, false), ACTOR);

        assertThat(applied.version()).isEqualTo(3);
        assertThat(applied.name()).isEqualTo("捨棄前的名稱");
        assertThat(applied.content()).isEqualTo("捨棄前的內容");
        assertThat(applied.basedOnVersion()).isEqualTo(2);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DISCARDED);
    }

    /** The rollback itself is pinned by AssistantConfigRepositoryImplTest#replaceWorkingCopyFailureRollsBackDiscard. */
    @Test
    void applyIsAtomic() {
        AssistantConfigRepository failing = delegatingRepository();
        DataAccessResourceFailureException boom = new DataAccessResourceFailureException("insert failed");
        doThrow(boom).when(failing).replaceWorkingCopy(any(), any());
        doThrow(boom).when(failing).insert(any(AssistantConfig.class));
        ConfigCommandService failingService = newService(failing);

        AssistantConfig draft = service.createDraft(KEY, OTHER);
        assertThatThrownBy(() -> failingService.applyVersion(KEY,
                new ApplyCommand(1, draft.docVersion(), true), ACTOR)).isSameAs(boom);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).docVersion()).isEqualTo(draft.docVersion());

        AssistantConfig scheduled = schedule(draft, NOW.plus(Duration.ofHours(1)), OTHER);
        assertThatThrownBy(() -> failingService.applyVersion(KEY,
                new ApplyCommand(1, scheduled.docVersion(), true), ACTOR)).isSameAs(boom);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(reload(2).updatedBy()).isEqualTo(OTHER);

        assertThat(versionCount()).isEqualTo(2);
    }

    @Test
    void applyWithStaleWorkingCopyDocVersionConflicts() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        AssistantConfig saved = save(draft, "聯繫準備", validContent("他人修改"), SaveMode.KEEP_STATUS, OTHER);

        assertError(() -> service.applyVersion(KEY, new ApplyCommand(1, draft.docVersion(), true), ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> service.applyVersion(KEY, new ApplyCommand(1, null, true), ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);
        assertThat(reload(2).status()).isEqualTo(ConfigStatus.DRAFT);

        // The schedule the user saw has since been published by the scheduler: no working copy left.
        AssistantConfig scheduled = schedule(saved, NOW.plus(Duration.ofMinutes(2)), OTHER);
        clock.advance(Duration.ofMinutes(3));
        assertThat(service.publishDue()).isEqualTo(1);
        assertError(() -> service.applyVersion(KEY, new ApplyCommand(1, scheduled.docVersion(), true), ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);
        assertThat(versionCount()).isEqualTo(2);
    }

    @Test
    void applyFromDraftOrScheduledIsInvalidState() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        assertError(() -> service.applyVersion(KEY, new ApplyCommand(2, draft.docVersion(), true), ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);

        AssistantConfig scheduled = schedule(draft, NOW.plus(Duration.ofHours(1)), OTHER);
        assertError(() -> service.applyVersion(KEY, new ApplyCommand(2, scheduled.docVersion(), true), ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);

        assertThat(reload(2).status()).isEqualTo(ConfigStatus.SCHEDULED);
    }

    @Test
    void applyUnknownVersionIsNotFound() {
        assertError(() -> service.applyVersion(KEY, new ApplyCommand(99, null, false), ACTOR),
                ErrorCode.AI_CONFIG_NOT_FOUND);
        assertError(() -> service.applyVersion("instruction.nope", new ApplyCommand(1, null, false), ACTOR),
                ErrorCode.AI_CONFIG_NOT_FOUND);
    }

    @Test
    void createKeySystemPromptMustBeSystemMain() {
        AssistantException e = assertError(
                () -> service.createKey("system.second", ConfigType.SYSTEM_PROMPT, "第二份", ACTOR),
                ErrorCode.AI_INPUT_INVALID);
        assertThat(e.details()).containsExactly(new FieldDetail("configKey"));
        assertThat(repository.findLatest("system.second")).isEmpty();
    }

    @Test
    void createKeyInstructionMustHaveInstructionPrefix() {
        AssistantException e = assertError(
                () -> service.createKey("system.x", ConfigType.SYSTEM_INSTRUCTION, "x", ACTOR),
                ErrorCode.AI_INPUT_INVALID);
        assertThat(e.details()).containsExactly(new FieldDetail("configKey"));
    }

    @Test
    void createKeyExistingKeyIsInvalidState() {
        assertError(() -> service.createKey("system.main", ConfigType.SYSTEM_PROMPT, "重複", ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);
        assertError(() -> service.createKey(KEY, ConfigType.SYSTEM_INSTRUCTION, "重複", ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);
    }

    @Test
    void createKeyLosingTheInsertRaceIsInvalidState() {
        AssistantConfigRepository racing = mock(AssistantConfigRepository.class);
        when(racing.findLatest("instruction.race")).thenReturn(Optional.empty());
        when(racing.insert(any())).thenThrow(new AssistantException(ErrorCode.AI_CONFIG_CONFLICT));

        assertError(() -> newService(racing).createKey("instruction.race", ConfigType.SYSTEM_INSTRUCTION, "x", ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);
    }

    @Test
    void createKeyPrefillsInstructionTemplate() {
        AssistantConfig created = service.createKey("instruction.new-topic", ConfigType.SYSTEM_INSTRUCTION,
                "新分類", ACTOR);

        assertThat(created.version()).isEqualTo(1);
        assertThat(created.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(created.name()).isEqualTo("新分類");
        assertThat(created.content()).contains("## 適用問題", "## 回答套版", "## 範例問題", "## 注意事項");
        assertThat(created.createdBy()).isEqualTo(ACTOR);
        assertThat(created.updatedBy()).isEqualTo(ACTOR);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(repository.findPublished("instruction.new-topic")).isEmpty();
        assertThat(repository.findWorkingCopy("instruction.new-topic")).hasValueSatisfying(
                wc -> assertThat(wc.status()).isEqualTo(ConfigStatus.DRAFT));
    }

    @Test
    void validateWorkingCopyReportsRuleViolations() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        assertThat(service.validateWorkingCopy(KEY)).isEmpty();
        save(draft, "聯繫準備", INVALID_CONTENT, SaveMode.KEEP_STATUS, OTHER);

        assertThat(service.validateWorkingCopy(KEY)).extracting(ValidationError::rule).contains("REQUIRED_SECTION");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

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
