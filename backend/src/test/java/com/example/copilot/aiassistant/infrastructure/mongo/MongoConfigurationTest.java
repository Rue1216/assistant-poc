package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MongoConfigurationTest extends IntegrationTestBase {

    @Autowired MongoTransactionManager transactionManager;
    @Autowired AssistantConfigMongoRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll(repository.findAll().stream()
                .filter(c -> c.getConfigKey().startsWith("instruction.tx-")).toList());
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
