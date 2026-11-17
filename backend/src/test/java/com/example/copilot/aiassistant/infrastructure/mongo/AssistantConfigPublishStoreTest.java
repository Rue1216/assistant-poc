package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.application.support.Transactions;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.example.copilot.testsupport.ConfigDocumentStore;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Document-level writes behind publishing, against the real replica set. The store works on documents only and
 * leaves the transaction to its caller, so these tests open one where a write spans two documents.
 */
class AssistantConfigPublishStoreTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-20T02:00:00Z");
    private static final Instant SCHEDULED_AT = NOW.plus(Duration.ofMinutes(2));
    private static final Instant RUN_AT = NOW.plus(Duration.ofMinutes(3));
    /** Seeded with a PUBLISHED v1. */
    private static final String KEY = "instruction.contact-prep";
    private static final String SCHEDULER = "EEE22222";

    @Autowired MongoTemplate mongo;
    @Autowired SeedLoader seedLoader;
    @Autowired @Qualifier(Transactions.MONGO) MongoTransactionManager transactionManager;

    private ConfigDocumentStore documents;
    private AssistantConfigPublishStore store;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        documents = new ConfigDocumentStore(mongo);
        store = new AssistantConfigPublishStore(mongo);
        tx = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void restoreSeed() {
        documents.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    private AssistantConfigDocument scheduled(String configKey, int version, Instant at) {
        AssistantConfigDocument doc = new AssistantConfigDocument();
        doc.setConfigKey(configKey);
        doc.setConfigType(ConfigType.SYSTEM_INSTRUCTION);
        doc.setName("預約的版本");
        doc.setContent("內容");
        doc.setStatus(ConfigStatus.SCHEDULED);
        doc.setVersion(version);
        doc.setCreatedBy(SCHEDULER);
        doc.setCreatedAt(NOW);
        doc.setUpdatedBy(SCHEDULER);
        doc.setUpdatedAt(NOW);
        doc.setScheduledAt(at);
        return documents.insert(doc);
    }

    private AssistantConfigDocument version(String configKey, int version) {
        return documents.findByConfigKeyOrderByVersionDesc(configKey).stream()
                .filter(doc -> doc.getVersion() == version).findFirst().orElseThrow();
    }

    @Test
    void findScheduledDueReturnsScheduledVersionsWhoseTimeHasCome() {
        scheduled(KEY, 2, SCHEDULED_AT);
        scheduled("instruction.later", 1, RUN_AT.plusSeconds(1));

        assertThat(store.findScheduledDue(SCHEDULED_AT.minusSeconds(1))).isEmpty();
        assertThat(store.findScheduledDue(RUN_AT))
                .extracting(AssistantConfigDocument::getConfigKey, AssistantConfigDocument::getVersion)
                .containsExactly(tuple(KEY, 2));
    }

    @Test
    void publishScheduledArchivesThePreviousVersionAndKeepsWhoScheduledIt() {
        AssistantConfigDocument v1Before = version(KEY, 1);
        AssistantConfigDocument due = scheduled(KEY, 2, SCHEDULED_AT);

        tx.executeWithoutResult(status -> store.publishScheduled(due, RUN_AT));

        AssistantConfigDocument v2 = version(KEY, 2);
        assertThat(v2.getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(v2.getUpdatedAt()).isEqualTo(RUN_AT);
        assertThat(v2.getUpdatedBy()).isEqualTo(SCHEDULER);
        assertThat(v2.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(v2.getDocVersion()).isEqualTo(due.getDocVersion() + 1);
        AssistantConfigDocument v1 = version(KEY, 1);
        assertThat(v1.getStatus()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.getUpdatedAt()).isEqualTo(v1Before.getUpdatedAt());
        assertThat(v1.getUpdatedBy()).isEqualTo(v1Before.getUpdatedBy());
        assertThat(v1.getDocVersion()).isEqualTo(v1Before.getDocVersion() + 1);
    }

    @Test
    void publishScheduledOfAKeyThatWasNeverPublishedHasNothingToArchive() {
        AssistantConfigDocument due = scheduled("instruction.brand-new", 1, SCHEDULED_AT);

        tx.executeWithoutResult(status -> store.publishScheduled(due, RUN_AT));

        assertThat(version("instruction.brand-new", 1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
    }

    /** The claim is refused by throwing, so that the caller's transaction undoes the archive as well. */
    @Test
    void publishScheduledOfAVersionChangedMeanwhileThrowsAndTheArchiveIsRolledBack() {
        AssistantConfigDocument v1Before = version(KEY, 1);
        AssistantConfigDocument stale = scheduled(KEY, 2, SCHEDULED_AT);
        AssistantConfigDocument edited = version(KEY, 2);
        edited.setName("改過的名稱");
        documents.save(edited);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.publishScheduled(stale, RUN_AT)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(version(KEY, 2).getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(version(KEY, 1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(version(KEY, 1).getDocVersion()).isEqualTo(v1Before.getDocVersion());
    }

    @Test
    void archivePublishedLeavesTheArchivedVersionsAuthorAndTimeAlone() {
        AssistantConfigDocument v1Before = version(KEY, 1);

        store.archivePublished(KEY);

        AssistantConfigDocument v1 = version(KEY, 1);
        assertThat(v1.getStatus()).isEqualTo(ConfigStatus.ARCHIVED);
        assertThat(v1.getUpdatedAt()).isEqualTo(v1Before.getUpdatedAt());
        assertThat(v1.getUpdatedBy()).isEqualTo(v1Before.getUpdatedBy());
    }

    @Test
    void revertScheduledTurnsItBackIntoADraft() {
        AssistantConfigDocument due = scheduled(KEY, 2, SCHEDULED_AT);

        assertThat(store.revertScheduled(due, "system", RUN_AT)).isTrue();

        AssistantConfigDocument v2 = version(KEY, 2);
        assertThat(v2.getStatus()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(v2.getScheduledAt()).isNull();
        assertThat(v2.getUpdatedBy()).isEqualTo("system");
        assertThat(v2.getUpdatedAt()).isEqualTo(RUN_AT);
        assertThat(v2.getDocVersion()).isEqualTo(due.getDocVersion() + 1);
        assertThat(version(KEY, 1).getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
    }

    @Test
    void revertScheduledLeavesAVersionChangedMeanwhileAlone() {
        AssistantConfigDocument stale = scheduled(KEY, 2, SCHEDULED_AT);
        AssistantConfigDocument edited = version(KEY, 2);
        edited.setName("改過的名稱");
        documents.save(edited);

        assertThat(store.revertScheduled(stale, "system", RUN_AT)).isFalse();

        AssistantConfigDocument v2 = version(KEY, 2);
        assertThat(v2.getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(v2.getUpdatedBy()).isEqualTo(SCHEDULER);
        assertThat(v2.getScheduledAt()).isEqualTo(SCHEDULED_AT);
    }
}
