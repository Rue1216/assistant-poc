package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.application.support.Transactions;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.testsupport.ConfigDocumentStore;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MongoConfigurationTest extends IntegrationTestBase {

    @Autowired @Qualifier(Transactions.MONGO) MongoTransactionManager transactionManager;
    @Autowired MongoTemplate mongo;
    ConfigDocumentStore repository;

    @Autowired
    void initRepository(MongoTemplate template) {
        repository = new ConfigDocumentStore(template);
    }

    @AfterEach
    void cleanup() {
        mongo.remove(Query.query(where("configKey").regex("^instruction[.]tx-")), AssistantConfigDocument.class);
    }

    private AssistantConfigDocument config(String key) {
        AssistantConfigDocument c = new AssistantConfigDocument();
        c.setConfigKey(key);
        c.setConfigType(ConfigType.SYSTEM_INSTRUCTION);
        c.setName("n");
        c.setContent("c");
        c.setStatus(ConfigStatus.ARCHIVED);
        c.setVersion(1);
        c.setCreatedBy("t");
        c.setCreatedAt(Instant.now());
        c.setUpdatedBy("t");
        c.setUpdatedAt(Instant.now());
        return c;
    }

    @Test
    void rollsBackBothWritesOnFailure() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            repository.save(config("instruction.tx-a"));
            repository.save(config("instruction.tx-b"));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findTopByConfigKeyOrderByVersionDesc("instruction.tx-a")).isEmpty();
        assertThat(repository.findTopByConfigKeyOrderByVersionDesc("instruction.tx-b")).isEmpty();
    }

    @Test
    void commitsBothWritesOnSuccess() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            repository.save(config("instruction.tx-a"));
            repository.save(config("instruction.tx-b"));
        });
        assertThat(repository.findTopByConfigKeyOrderByVersionDesc("instruction.tx-a")).isPresent();
        assertThat(repository.findTopByConfigKeyOrderByVersionDesc("instruction.tx-b")).isPresent();
    }
}
