package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.application.config.ConfigSummary;
import com.example.copilot.aiassistant.application.config.VersionHistoryEntry;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;

class ConfigQueryRepositoryImplTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-20T02:00:00Z");
    private static final String KEY = "instruction.contact-prep";

    @Autowired AssistantConfigMongoRepository mongoRepository;
    @Autowired MongoTemplate mongo;
    @Autowired SeedLoader seedLoader;

    private ConfigQueryRepositoryImpl queries;

    @BeforeEach
    void setUp() {
        queries = new ConfigQueryRepositoryImpl(mongo);
    }

    @AfterEach
    void restoreSeed() {
        mongoRepository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    private AssistantConfig insertDraft(String name) {
        AssistantConfig published = mongoRepository.findByConfigKeyAndStatus(KEY, ConfigStatus.PUBLISHED)
                .map(AssistantConfigMapper::toDomain).orElseThrow();
        AssistantConfig draft = AssistantConfig.newDraft(new ConfigKey(KEY), ConfigType.SYSTEM_INSTRUCTION, name,
                published.content(), 2, "EEE11111", NOW);
        return AssistantConfigMapper.toDomain(mongoRepository.insert(AssistantConfigMapper.toDocument(draft)));
    }

    @Test
    void listSummariesGroupsByKeyAndPrefersPublishedName() {
        insertDraft("Renamed draft");

        List<ConfigSummary> list = queries.listSummaries();

        assertThat(list).hasSize(8);
        assertThat(list.get(0).configKey()).isEqualTo("system.main");
        ConfigSummary contactPrep = list.stream().filter(s -> s.configKey().equals(KEY)).findFirst().orElseThrow();
        String publishedName = mongoRepository.findByConfigKeyAndStatus(KEY, ConfigStatus.PUBLISHED)
                .orElseThrow().getName();
        assertThat(contactPrep.name()).isEqualTo(publishedName).isNotEqualTo("Renamed draft");
        assertThat(contactPrep.published().version()).isEqualTo(1);
        assertThat(contactPrep.published().status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(contactPrep.workingCopy().version()).isEqualTo(2);
        assertThat(contactPrep.workingCopy().status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(contactPrep.workingCopy().updatedBy()).isEqualTo("EEE11111");
        assertThat(list).filteredOn(s -> !s.configKey().equals(KEY))
                .allSatisfy(s -> assertThat(s.workingCopy()).isNull());
    }

    @Test
    void listSummariesCarriesScheduledAtOfWorkingCopy() {
        Instant at = NOW.plusSeconds(3600);
        AssistantConfig draft = insertDraft("Scheduled draft");
        mongoRepository.save(AssistantConfigMapper.toDocument(draft.withStatus(ConfigStatus.SCHEDULED, at)));

        ConfigSummary contactPrep = queries.listSummaries().stream()
                .filter(s -> s.configKey().equals(KEY)).findFirst().orElseThrow();

        assertThat(contactPrep.workingCopy().status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(contactPrep.workingCopy().scheduledAt()).isEqualTo(at);
    }

    @Test
    void listSummariesShowsKeyWithoutPublishedVersion() {
        mongoRepository.insert(AssistantConfigMapper.toDocument(AssistantConfig.newDraft(new ConfigKey("instruction.new-topic"),
                ConfigType.SYSTEM_INSTRUCTION, "New topic", "body", 1, "EEE11111", NOW)));

        assertThat(queries.listSummaries()).anySatisfy(s -> {
            assertThat(s.configKey()).isEqualTo("instruction.new-topic");
            assertThat(s.name()).isEqualTo("New topic");
            assertThat(s.published()).isNull();
            assertThat(s.workingCopy().status()).isEqualTo(ConfigStatus.DRAFT);
        });
    }

    @Test
    void listSummariesShowsKeyWhoseOnlyVersionIsDiscarded() {
        AssistantConfig dropped = AssistantConfig.newDraft(new ConfigKey("instruction.dropped"),
                ConfigType.SYSTEM_INSTRUCTION, "Dropped", "body", 1, "EEE11111", NOW);
        mongoRepository.insert(AssistantConfigMapper.toDocument(dropped.withStatus(ConfigStatus.DISCARDED, null)));

        assertThat(queries.listSummaries()).anySatisfy(s -> {
            assertThat(s.configKey()).isEqualTo("instruction.dropped");
            assertThat(s.name()).isEqualTo("Dropped");
            assertThat(s.published()).isNull();
            assertThat(s.workingCopy()).isNull();
        });
    }

    @Test
    void listQueriesReadOnlySummaryFields() {
        Set<String> fields = Set.of("configKey", "configType", "name", "status", "version",
                "scheduledAt", "updatedBy", "updatedAt");
        assertThat(ConfigQueryRepositoryImpl.activeVersionsQuery().getFieldsObject().keySet()).isEqualTo(fields);
        assertThat(ConfigQueryRepositoryImpl.latestVersionQuery(KEY).getFieldsObject().keySet()).isEqualTo(fields);
        assertThat(ConfigQueryRepositoryImpl.historyQuery(KEY).getFieldsObject().get("content")).isEqualTo(0);
    }

    @Test
    void versionHistoryNewestFirst() {
        insertDraft("Draft");

        List<VersionHistoryEntry> history = queries.versionHistory(new ConfigKey(KEY));

        assertThat(history).extracting(VersionHistoryEntry::version).containsExactly(2, 1);
        assertThat(history.get(0).status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(history.get(0).createdBy()).isEqualTo("EEE11111");
        assertThat(history.get(1).status()).isEqualTo(ConfigStatus.PUBLISHED);
    }

    @Test
    void versionHistoryUnknownKeyIsEmpty() {
        assertThat(queries.versionHistory(new ConfigKey("instruction.nope"))).isEmpty();
    }
}
