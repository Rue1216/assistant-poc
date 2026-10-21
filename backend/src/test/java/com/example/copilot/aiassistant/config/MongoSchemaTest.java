package com.example.copilot.aiassistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.copilot.IntegrationTestBase;
import com.mongodb.MongoWriteException;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

class MongoSchemaTest extends IntegrationTestBase {

    private static final String CONFIG = "COLL_ICRM_AI_ASSISTANT_CONFIG";
    private static final String AUDIT = "COLL_ICRM_AI_ASSISTANT_AUDIT";

    @Autowired MongoTemplate mongo;
    @Autowired AssistantConfigRepository repository;
    @Autowired SeedLoader seedLoader;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @AfterEach
    void restoreSeed() {
        repository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    private Document rawDoc(String status, Object scheduledAt) {
        return new Document("configKey", "instruction.raw")
                .append("configType", "SYSTEM_INSTRUCTION")
                .append("name", "raw")
                .append("content", "c")
                .append("status", status)
                .append("version", 1)
                .append("createdBy", "t")
                .append("createdAt", new Date())
                .append("updatedBy", "t")
                .append("updatedAt", new Date())
                .append("scheduledAt", scheduledAt)
                .append("docVersion", 0L);
    }

    private AssistantConfig config(String key, int version, ConfigStatus status) {
        AssistantConfig c = new AssistantConfig();
        c.setConfigKey(key);
        c.setConfigType(ConfigType.SYSTEM_INSTRUCTION);
        c.setName("n");
        c.setContent("c");
        c.setStatus(status);
        c.setVersion(version);
        c.setCreatedBy("t");
        c.setCreatedAt(Instant.now());
        c.setUpdatedBy("t");
        c.setUpdatedAt(Instant.now());
        return c;
    }

    @Test
    void validatorRejectsUnknownStatus() {
        assertThatThrownBy(() -> mongo.getCollection(CONFIG).insertOne(rawDoc("LIVE", null)))
                .isInstanceOf(MongoWriteException.class);
    }

    @Test
    void validatorAcceptsDiscarded() {
        mongo.getCollection(CONFIG).insertOne(rawDoc("DISCARDED", new Date()));
        assertThat(repository.findByConfigKeyAndVersion("instruction.raw", 1).orElseThrow().getStatus())
                .isEqualTo(ConfigStatus.DISCARDED);
    }

    @Test
    void validatorRequiresScheduledAtForScheduled() {
        assertThatThrownBy(() -> mongo.getCollection(CONFIG).insertOne(rawDoc("SCHEDULED", null)))
                .isInstanceOf(MongoWriteException.class);
        mongo.getCollection(CONFIG).insertOne(rawDoc("SCHEDULED", new Date()));
    }

    @Test
    void uniqueVersionPerKey() {
        repository.save(config("instruction.dup", 1, ConfigStatus.ARCHIVED));
        assertThatThrownBy(() -> repository.save(config("instruction.dup", 1, ConfigStatus.ARCHIVED)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void onlyOnePublishedPerKey() {
        repository.save(config("instruction.dup", 1, ConfigStatus.PUBLISHED));
        assertThatThrownBy(() -> repository.save(config("instruction.dup", 2, ConfigStatus.PUBLISHED)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void indexNamesMatchSpec() {
        Set<String> config = mongo.indexOps(CONFIG).getIndexInfo().stream()
                .map(IndexInfo::getName).collect(Collectors.toSet());
        Set<String> audit = mongo.indexOps(AUDIT).getIndexInfo().stream()
                .map(IndexInfo::getName).collect(Collectors.toSet());
        assertThat(config).contains("UK_ICRM_AI_CFG_1", "UK_ICRM_AI_CFG_2", "UK_ICRM_AI_CFG_3",
                "IX_ICRM_AI_CFG_4", "IX_ICRM_AI_CFG_5");
        assertThat(audit).contains("IX_ICRM_AI_AUDIT_1", "IX_ICRM_AI_AUDIT_2", "IX_ICRM_AI_AUDIT_3");
        IndexInfo ttl = mongo.indexOps(AUDIT).getIndexInfo().stream()
                .filter(i -> i.getName().equals("IX_ICRM_AI_AUDIT_1")).findFirst().orElseThrow();
        assertThat(ttl.getExpireAfter().orElseThrow().toSeconds()).isEqualTo(15_552_000L);
    }
}
