package com.example.copilot.aiassistant.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
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
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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

/** Each test maps to a row of the state-transition table in 04 section 2.4. */
class ConfigServiceTest extends IntegrationTestBase {

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
    @Autowired MongoTransactionManager transactionManager;
    @Autowired AiAssistantProperties properties;
    @Autowired SeedLoader seedLoader;

    private MutableClock clock;
    private PublishedConfigCache cache;
    private ConfigService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        cache = mock(PublishedConfigCache.class);
        service = newService(repository);
    }

    @AfterEach
    void restoreSeed() {
        repository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    private ConfigService newService(AssistantConfigRepository repo) {
        return new ConfigService(repo, mongo, validator, cache, transactionManager, clock, properties);
    }

    private AssistantConfig reload(int version) {
        return repository.findByConfigKeyAndVersion(KEY, version).orElseThrow();
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
        return reload(1).getContent() + "\n- " + extraNote + "\n";
    }

    private AssistantConfig save(AssistantConfig current, String name, String content, SaveMode mode, String actor) {
        return service.saveWorkingCopy(KEY, new SaveCommand(name, content, "note", mode, current.getDocVersion()), actor);
    }

    private AssistantConfig schedule(AssistantConfig current, Instant at, String actor) {
        return service.publish(KEY, new PublishCommand(PublishMode.SCHEDULED, at, current.getDocVersion()), actor);
    }

    private AssistantConfig publishNow(AssistantConfig current, String actor) {
        return service.publish(KEY, new PublishCommand(PublishMode.IMMEDIATE, null, current.getDocVersion()), actor);
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

        assertThat(draft.getVersion()).isEqualTo(2);
        assertThat(draft.getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(draft.getName()).isEqualTo(v1.getName());
        assertThat(draft.getContent()).isEqualTo(v1.getContent());
        assertThat(draft.getConfigType()).isEqualTo(ConfigType.SYSTEM_INSTRUCTION);
        assertThat(draft.getBasedOnVersion()).isNull();
        assertThat(draft.getChangeNote()).isNull();
        assertThat(draft.getScheduledAt()).isNull();
        assertThat(draft.getCreatedBy()).isEqualTo(ACTOR);
        assertThat(draft.getCreatedAt()).isEqualTo(NOW);
        assertThat(draft.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(draft.getUpdatedAt()).isEqualTo(NOW);
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
    }

    @Test
    void createDraftFailsWhenWorkingCopyExists() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        assertError(() -> service.createDraft(KEY, ACTOR), ErrorCode.AI_CONFIG_INVALID_STATE);

        schedule(draft, NOW.plus(Duration.ofHours(1)), ACTOR);
        assertError(() -> service.createDraft(KEY, ACTOR), ErrorCode.AI_CONFIG_INVALID_STATE);

        assertThat(repository.findByConfigKeyOrderByVersionDesc(KEY)).hasSize(2);
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

        assertThat(saved.getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(saved.getName()).isEqualTo("新名稱");
        assertThat(saved.getContent()).isEqualTo(INVALID_CONTENT);
        assertThat(saved.getChangeNote()).isEqualTo("note");
        assertThat(saved.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(saved.getCreatedBy()).isEqualTo(OTHER);
        assertThat(saved.getDocVersion()).isEqualTo(draft.getDocVersion() + 1);
        assertThat(reload(2).getContent()).isEqualTo(INVALID_CONTENT);

        AssistantConfig asDraft = save(saved, "新名稱", "x", SaveMode.DRAFT, ACTOR);
        assertThat(asDraft.getStatus()).isEqualTo(ConfigStatus.DRAFT);
    }

    @Test
    void saveKeepStatusOnScheduledKeepsScheduleAndRevalidates() {
        Instant at = NOW.plus(Duration.ofHours(1));
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), at, OTHER);
        clock.advance(Duration.ofMinutes(1));

        String content = validContent("排程中修改");
        AssistantConfig saved = save(scheduled, "聯繫準備", content, SaveMode.KEEP_STATUS, ACTOR);

        assertThat(saved.getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(saved.getScheduledAt()).isEqualTo(at);
        assertThat(saved.getContent()).isEqualTo(content);
        assertThat(saved.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));

        AssistantException e = assertError(
                () -> save(saved, "聯繫準備", INVALID_CONTENT, SaveMode.KEEP_STATUS, OTHER),
                ErrorCode.AI_CONFIG_VALIDATION_FAILED);
        assertThat(e.details()).isNotEmpty().allSatisfy(d -> assertThat(d).isInstanceOf(ValidationError.class));

        AssistantConfig after = reload(2);
        assertThat(after.getContent()).isEqualTo(content);
        assertThat(after.getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(after.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(after.getDocVersion()).isEqualTo(saved.getDocVersion());
    }

    @Test
    void saveAsDraftCancelsSchedule() {
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), NOW.plus(Duration.ofHours(1)), OTHER);

        AssistantConfig saved = save(scheduled, "聯繫準備", INVALID_CONTENT, SaveMode.DRAFT, ACTOR);

        assertThat(saved.getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(saved.getScheduledAt()).isNull();
        assertThat(saved.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).getScheduledAt()).isNull();
    }

    @Test
    void staleDocVersionConflicts() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);
        AssistantConfig saved = save(draft, "聯繫準備", validContent("a"), SaveMode.KEEP_STATUS, ACTOR);

        assertError(() -> save(draft, "聯繫準備", "stale", SaveMode.KEEP_STATUS, OTHER),
                ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> publishNow(draft, OTHER), ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> schedule(draft, NOW.plus(Duration.ofHours(1)), OTHER), ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> service.discardDraft(KEY, draft.getDocVersion(), OTHER), ErrorCode.AI_CONFIG_CONFLICT);

        AssistantConfig after = reload(2);
        assertThat(after.getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(after.getDocVersion()).isEqualTo(saved.getDocVersion());
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
    }

    /** A delegating repository whose working-copy / published reads can be intercepted. */
    private AssistantConfigRepository delegatingRepository() {
        return mock(AssistantConfigRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
    }

    private void bumpDocVersion(AssistantConfig c) {
        mongo.updateFirst(Query.query(where("id").is(c.getId())), new Update().inc("docVersion", 1L),
                AssistantConfig.class);
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

        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).getDocVersion()).isEqualTo(draft.getDocVersion());
        verify(cache, never()).invalidate();
    }

    @Test
    void docVersionChangedBetweenReadAndSaveIsConflict() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        AssistantConfigRepository racing = delegatingRepository();
        doAnswer(inv -> {
            List<AssistantConfig> read = repository.findByConfigKeyAndStatusIn(inv.getArgument(0), inv.getArgument(1));
            bumpDocVersion(draft);
            return read;
        }).when(racing).findByConfigKeyAndStatusIn(any(), any());

        assertError(() -> newService(racing).saveWorkingCopy(KEY,
                new SaveCommand("聯繫準備", "競爭中的儲存", null, SaveMode.KEEP_STATUS, draft.getDocVersion()), ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);

        assertThat(reload(2).getContent()).isEqualTo(draft.getContent());
        assertThat(reload(2).getUpdatedBy()).isEqualTo(OTHER);
    }

    @Test
    void publishedVersionChangedBeforeArchiveIsConflict() {
        AssistantConfig v1Before = reload(1);
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        AssistantConfigRepository racing = delegatingRepository();
        doAnswer(inv -> {
            Optional<AssistantConfig> read = repository.findByConfigKeyAndStatus(inv.getArgument(0), inv.getArgument(1));
            read.ifPresent(this::bumpDocVersion);
            return read;
        }).when(racing).findByConfigKeyAndStatus(any(), any());

        assertError(() -> newService(racing).publish(KEY,
                new PublishCommand(PublishMode.IMMEDIATE, null, draft.getDocVersion()), ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);

        // The bump ran inside the publishing transaction, so it was rolled back with everything else.
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(1).getDocVersion()).isEqualTo(v1Before.getDocVersion());
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DRAFT);
        verify(cache, never()).invalidate();
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
        doAnswer(inv -> {
            List<AssistantConfig> read = repository.findByConfigKeyAndStatusIn(inv.getArgument(0), inv.getArgument(1));
            read.forEach(c -> c.setName(tooLong));
            return read;
        }).when(corrupting).findByConfigKeyAndStatusIn(any(), any());
        assertThatThrownBy(() -> newService(corrupting).publish(KEY,
                new PublishCommand(PublishMode.IMMEDIATE, null, draft.getDocVersion()), ACTOR))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().isInstanceOfSatisfying(MongoException.class, e -> assertThat(e.getCode()).isEqualTo(121));

        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DRAFT);
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

        assertThat(published.getVersion()).isEqualTo(2);
        assertThat(published.getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(published.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(published.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);

        AssistantConfig v1 = reload(1);
        assertThat(v1.getStatus()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.getUpdatedBy()).isEqualTo(v1Before.getUpdatedBy());
        assertThat(v1.getUpdatedAt()).isEqualTo(v1Before.getUpdatedAt());
        assertThat(v1.getContent()).isEqualTo(v1Before.getContent());

        assertThat(repository.findByConfigKeyAndStatusIn(KEY, List.of(ConfigStatus.PUBLISHED))).hasSize(1);
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

        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).getDocVersion()).isEqualTo(draft.getDocVersion());
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        verify(cache, never()).invalidate();
    }

    @Test
    void scheduleSetsScheduledAt() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        Instant at = NOW.plus(Duration.ofMinutes(2));

        AssistantConfig scheduled = schedule(draft, at, ACTOR);

        assertThat(scheduled.getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(scheduled.getScheduledAt()).isEqualTo(at);
        assertThat(scheduled.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(scheduled.getUpdatedAt()).isEqualTo(NOW);
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        verify(cache, never()).invalidate();
    }

    @Test
    void scheduleOutsideWindowIsInputInvalid() {
        AssistantConfig draft = service.createDraft(KEY, ACTOR);

        for (Instant at : new Instant[] {NOW.plusSeconds(30), NOW.plus(Duration.ofDays(91)), null}) {
            AssistantException e = assertError(() -> schedule(draft, at, ACTOR), ErrorCode.AI_INPUT_INVALID);
            assertThat(e.details()).containsExactly(Map.of("field", "scheduledAt"));
        }
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DRAFT);

        // Both bounds are inclusive.
        AssistantConfig atMin = schedule(draft, NOW.plus(Duration.ofMinutes(1)), ACTOR);
        AssistantConfig atMax = schedule(atMin, NOW.plus(Duration.ofDays(90)), ACTOR);
        assertThat(atMax.getScheduledAt()).isEqualTo(NOW.plus(Duration.ofDays(90)));
    }

    @Test
    void rescheduleChangesTime() {
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), NOW.plus(Duration.ofHours(1)), OTHER);
        clock.advance(Duration.ofMinutes(3));

        AssistantConfig moved = schedule(scheduled, NOW.plus(Duration.ofHours(2)), ACTOR);

        assertThat(moved.getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(moved.getScheduledAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(moved.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(moved.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3)));
        assertThat(reload(2).getScheduledAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
    }

    @Test
    void publishImmediateOnScheduledPublishesNow() {
        AssistantConfig v1Before = reload(1);
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), NOW.plus(Duration.ofHours(1)), OTHER);
        clock.advance(Duration.ofMinutes(5));

        AssistantConfig published = publishNow(scheduled, ACTOR);

        assertThat(published.getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(published.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(published.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        AssistantConfig v1 = reload(1);
        assertThat(v1.getStatus()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.getUpdatedAt()).isEqualTo(v1Before.getUpdatedAt());
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
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        verify(cache, never()).invalidate();

        clock.advance(Duration.ofMinutes(1).plusSeconds(20));
        assertThat(service.publishDue()).isEqualTo(1);

        AssistantConfig v2 = reload(2);
        assertThat(v2.getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(v2.getUpdatedBy()).isEqualTo(OTHER);
        assertThat(v2.getUpdatedAt()).isEqualTo(at.plusSeconds(20));
        assertThat(v2.getScheduledAt()).isEqualTo(at);
        AssistantConfig v1 = reload(1);
        assertThat(v1.getStatus()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.getUpdatedBy()).isEqualTo(v1Before.getUpdatedBy());
        assertThat(v1.getUpdatedAt()).isEqualTo(v1Before.getUpdatedAt());
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
            List<AssistantConfig> stale = repository.findByStatusAndScheduledAtLessThanEqual(
                    inv.getArgument(0), inv.getArgument(1));
            save(scheduled, "聯繫準備", validContent("搶先儲存"), SaveMode.KEEP_STATUS, ACTOR);
            return stale;
        }).when(racing).findByStatusAndScheduledAtLessThanEqual(any(), any());

        assertThat(newService(racing).publishDue()).isZero();

        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        verify(cache, never()).invalidate();
    }

    @Test
    void discardOnlyAllowedForDraft() {
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), NOW.plus(Duration.ofHours(1)), OTHER);

        assertError(() -> service.discardDraft(KEY, scheduled.getDocVersion(), ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);

        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
    }

    @Test
    void discardDraftIsSoft() {
        AssistantConfig draft = save(service.createDraft(KEY, OTHER), "聯繫準備", "未完成的草稿",
                SaveMode.KEEP_STATUS, OTHER);
        clock.advance(Duration.ofMinutes(7));

        service.discardDraft(KEY, draft.getDocVersion(), ACTOR);

        AssistantConfig discarded = reload(2);
        assertThat(discarded.getStatus()).isEqualTo(ConfigStatus.DISCARDED);
        assertThat(discarded.getContent()).isEqualTo("未完成的草稿");
        assertThat(discarded.getCreatedBy()).isEqualTo(OTHER);
        assertThat(discarded.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(discarded.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(7)));

        AssistantConfig next = service.createDraft(KEY, ACTOR);
        assertThat(next.getVersion()).isEqualTo(3);
        assertThat(next.getContent()).isEqualTo(reload(1).getContent());
    }

    @Test
    void applyWithoutWorkingCopyCreatesDraft() {
        AssistantConfig v1 = reload(1);
        AssistantConfig v2 = publishV2();
        clock.advance(Duration.ofMinutes(30));

        AssistantConfig applied = service.applyVersion(KEY, new ApplyCommand(1, null, false), OTHER);

        assertThat(applied.getVersion()).isEqualTo(3);
        assertThat(applied.getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(applied.getName()).isEqualTo(v1.getName());
        assertThat(applied.getContent()).isEqualTo(v1.getContent());
        assertThat(applied.getBasedOnVersion()).isEqualTo(1);
        assertThat(applied.getChangeNote()).isNull();
        assertThat(applied.getScheduledAt()).isNull();
        assertThat(applied.getCreatedBy()).isEqualTo(OTHER);
        assertThat(applied.getUpdatedBy()).isEqualTo(OTHER);
        assertThat(applied.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));

        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).getDocVersion()).isEqualTo(v2.getDocVersion());
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.ARCHIVED);
        verify(cache, never()).invalidate();
    }

    @Test
    void applyOverDraftRequiresConfirmationAndDiscardsIt() {
        AssistantConfig v1 = reload(1);
        publishV2();
        AssistantConfig draft = save(service.createDraft(KEY, OTHER), "聯繫準備", "草稿內容",
                SaveMode.KEEP_STATUS, OTHER);

        for (Long seen : new Long[] {null, draft.getDocVersion()}) {
            AssistantException e = assertError(
                    () -> service.applyVersion(KEY, new ApplyCommand(1, seen, false), ACTOR),
                    ErrorCode.AI_CONFIG_OVERWRITE_PENDING);
            assertThat(e.details()).hasSize(1);
            Map<?, ?> pending = (Map<?, ?>) e.details().get(0);
            assertThat(pending.get("status")).isEqualTo("DRAFT");
            assertThat(pending.get("scheduledAt")).isNull();
            assertThat(pending.get("updatedAt")).isEqualTo(NOW);
            assertThat(pending.get("docVersion")).isEqualTo(draft.getDocVersion());
        }
        assertThat(reload(3).getStatus()).isEqualTo(ConfigStatus.DRAFT);

        clock.advance(Duration.ofMinutes(4));
        AssistantConfig applied = service.applyVersion(KEY, new ApplyCommand(1, draft.getDocVersion(), true), ACTOR);

        assertThat(applied.getVersion()).isEqualTo(4);
        assertThat(applied.getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(applied.getContent()).isEqualTo(v1.getContent());
        assertThat(applied.getBasedOnVersion()).isEqualTo(1);

        AssistantConfig replaced = reload(3);
        assertThat(replaced.getStatus()).isEqualTo(ConfigStatus.DISCARDED);
        assertThat(replaced.getContent()).isEqualTo("草稿內容");
        assertThat(replaced.getCreatedBy()).isEqualTo(OTHER);
        assertThat(replaced.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(replaced.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        verify(cache, never()).invalidate();
    }

    @Test
    void applyOverScheduledDiscardsItAndCancelsSchedule() {
        Instant at = NOW.plus(Duration.ofHours(1));
        AssistantConfig scheduled = schedule(service.createDraft(KEY, OTHER), at, OTHER);

        AssistantException e = assertError(
                () -> service.applyVersion(KEY, new ApplyCommand(1, scheduled.getDocVersion(), false), ACTOR),
                ErrorCode.AI_CONFIG_OVERWRITE_PENDING);
        Map<?, ?> pending = (Map<?, ?>) e.details().get(0);
        assertThat(pending.get("status")).isEqualTo("SCHEDULED");
        assertThat(pending.get("scheduledAt")).isEqualTo(at);

        AssistantConfig applied = service.applyVersion(KEY, new ApplyCommand(1, scheduled.getDocVersion(), true), ACTOR);

        assertThat(applied.getVersion()).isEqualTo(3);
        assertThat(applied.getStatus()).isEqualTo(ConfigStatus.DRAFT);
        AssistantConfig replaced = reload(2);
        assertThat(replaced.getStatus()).isEqualTo(ConfigStatus.DISCARDED);
        assertThat(replaced.getScheduledAt()).isEqualTo(at);
        assertThat(replaced.getUpdatedBy()).isEqualTo(ACTOR);

        clock.advance(Duration.ofHours(2));
        assertThat(service.publishDue()).isZero();
        assertThat(reload(1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DISCARDED);
        assertThat(reload(3).getStatus()).isEqualTo(ConfigStatus.DRAFT);
    }

    @Test
    void applyFromDiscardedRestoresContent() {
        AssistantConfig draft = save(service.createDraft(KEY, OTHER), "捨棄前的名稱", "捨棄前的內容",
                SaveMode.KEEP_STATUS, OTHER);
        service.discardDraft(KEY, draft.getDocVersion(), OTHER);

        AssistantConfig applied = service.applyVersion(KEY, new ApplyCommand(2, null, false), ACTOR);

        assertThat(applied.getVersion()).isEqualTo(3);
        assertThat(applied.getName()).isEqualTo("捨棄前的名稱");
        assertThat(applied.getContent()).isEqualTo("捨棄前的內容");
        assertThat(applied.getBasedOnVersion()).isEqualTo(2);
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DISCARDED);
    }

    @Test
    void applyIsAtomic() {
        AssistantConfigRepository failing = mock(AssistantConfigRepository.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
        DataAccessResourceFailureException boom = new DataAccessResourceFailureException("insert failed");
        doThrow(boom).when(failing).insert(any(AssistantConfig.class));
        doThrow(boom).when(failing).save(argThat((AssistantConfig c) -> c != null && c.getId() == null));
        ConfigService failingService = newService(failing);

        AssistantConfig draft = service.createDraft(KEY, OTHER);
        assertThatThrownBy(() -> failingService.applyVersion(KEY,
                new ApplyCommand(1, draft.getDocVersion(), true), ACTOR)).isSameAs(boom);
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(reload(2).getDocVersion()).isEqualTo(draft.getDocVersion());

        AssistantConfig scheduled = schedule(draft, NOW.plus(Duration.ofHours(1)), OTHER);
        assertThatThrownBy(() -> failingService.applyVersion(KEY,
                new ApplyCommand(1, scheduled.getDocVersion(), true), ACTOR)).isSameAs(boom);
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(reload(2).getUpdatedBy()).isEqualTo(OTHER);

        assertThat(repository.findByConfigKeyOrderByVersionDesc(KEY)).hasSize(2);
    }

    @Test
    void applyWithStaleWorkingCopyDocVersionConflicts() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        AssistantConfig saved = save(draft, "聯繫準備", validContent("他人修改"), SaveMode.KEEP_STATUS, OTHER);

        assertError(() -> service.applyVersion(KEY, new ApplyCommand(1, draft.getDocVersion(), true), ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> service.applyVersion(KEY, new ApplyCommand(1, null, true), ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);
        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.DRAFT);

        // The schedule the user saw has since been published by the scheduler: no working copy left.
        AssistantConfig scheduled = schedule(saved, NOW.plus(Duration.ofMinutes(2)), OTHER);
        clock.advance(Duration.ofMinutes(3));
        assertThat(service.publishDue()).isEqualTo(1);
        assertError(() -> service.applyVersion(KEY, new ApplyCommand(1, scheduled.getDocVersion(), true), ACTOR),
                ErrorCode.AI_CONFIG_CONFLICT);
        assertThat(repository.findByConfigKeyOrderByVersionDesc(KEY)).hasSize(2);
    }

    @Test
    void applyFromDraftOrScheduledIsInvalidState() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        assertError(() -> service.applyVersion(KEY, new ApplyCommand(2, draft.getDocVersion(), true), ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);

        AssistantConfig scheduled = schedule(draft, NOW.plus(Duration.ofHours(1)), OTHER);
        assertError(() -> service.applyVersion(KEY, new ApplyCommand(2, scheduled.getDocVersion(), true), ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);

        assertThat(reload(2).getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
    }

    @Test
    void applyUnknownVersionIsNotFound() {
        assertError(() -> service.applyVersion(KEY, new ApplyCommand(99, null, false), ACTOR),
                ErrorCode.AI_CONFIG_NOT_FOUND);
        assertError(() -> service.applyVersion("instruction.nope", new ApplyCommand(1, null, false), ACTOR),
                ErrorCode.AI_CONFIG_NOT_FOUND);
    }

    @Test
    void createKeyRejectsSecondSystemPrompt() {
        assertError(() -> service.createKey("system.second", ConfigType.SYSTEM_PROMPT, "第二份", ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);
        assertError(() -> service.createKey(KEY, ConfigType.SYSTEM_INSTRUCTION, "重複", ACTOR),
                ErrorCode.AI_CONFIG_INVALID_STATE);
        assertThat(repository.findTopByConfigKeyOrderByVersionDesc("system.second")).isEmpty();
    }

    @Test
    void createKeyWithMismatchedTypeIsInputInvalid() {
        AssistantException e = assertError(
                () -> service.createKey("instruction.x", ConfigType.SYSTEM_PROMPT, "x", ACTOR),
                ErrorCode.AI_INPUT_INVALID);
        assertThat(e.details()).containsExactly(Map.of("field", "configType"));
    }

    @Test
    void createKeyPrefillsInstructionTemplate() {
        AssistantConfig created = service.createKey("instruction.new-topic", ConfigType.SYSTEM_INSTRUCTION,
                "新分類", ACTOR);

        assertThat(created.getVersion()).isEqualTo(1);
        assertThat(created.getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(created.getName()).isEqualTo("新分類");
        assertThat(created.getContent()).contains("## 適用問題", "## 回答套版", "## 範例問題", "## 注意事項");
        assertThat(created.getCreatedBy()).isEqualTo(ACTOR);
        assertThat(created.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
        assertThat(service.list()).anySatisfy(s -> {
            assertThat(s.configKey()).isEqualTo("instruction.new-topic");
            assertThat(s.published()).isNull();
            assertThat(s.workingCopy().status()).isEqualTo(ConfigStatus.DRAFT);
        });
    }

    @Test
    void listShowsPublishedAndWorkingCopy() {
        Instant at = NOW.plus(Duration.ofHours(1));
        schedule(service.createDraft(KEY, OTHER), at, OTHER);

        List<ConfigSummary> list = service.list();

        assertThat(list).hasSize(8);
        assertThat(list.get(0).configKey()).isEqualTo("system.main");
        ConfigSummary contactPrep = list.stream().filter(s -> s.configKey().equals(KEY)).findFirst().orElseThrow();
        assertThat(contactPrep.configType()).isEqualTo(ConfigType.SYSTEM_INSTRUCTION);
        assertThat(contactPrep.name()).isEqualTo(reload(1).getName());
        assertThat(contactPrep.published().version()).isEqualTo(1);
        assertThat(contactPrep.published().status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(contactPrep.workingCopy().version()).isEqualTo(2);
        assertThat(contactPrep.workingCopy().status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(contactPrep.workingCopy().scheduledAt()).isEqualTo(at);
        assertThat(contactPrep.workingCopy().updatedBy()).isEqualTo(OTHER);
        assertThat(list).filteredOn(s -> !s.configKey().equals(KEY))
                .allSatisfy(s -> assertThat(s.workingCopy()).isNull());
    }

    @Test
    void versionsAndValidateWorkingCopy() {
        AssistantConfig draft = service.createDraft(KEY, OTHER);
        assertThat(service.validateWorkingCopy(KEY)).isEmpty();
        save(draft, "聯繫準備", INVALID_CONTENT, SaveMode.KEEP_STATUS, OTHER);

        assertThat(service.validateWorkingCopy(KEY)).extracting(ValidationError::rule).contains("REQUIRED_SECTION");
        assertThat(service.versions(KEY)).extracting(AssistantConfig::getVersion).containsExactly(2, 1);
        assertThat(service.version(KEY, 2).getContent()).isEqualTo(INVALID_CONTENT);
        assertError(() -> service.version(KEY, 3), ErrorCode.AI_CONFIG_NOT_FOUND);
        assertError(() -> service.versions("instruction.nope"), ErrorCode.AI_CONFIG_NOT_FOUND);
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
