package com.example.copilot.aiassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

class SeedLoaderTest extends IntegrationTestBase {

    @Autowired AssistantConfigRepository repository;
    @Autowired SeedLoader seedLoader;

    @BeforeEach
    void cleanAndSeed() {
        repository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    @AfterEach
    void restoreSeed() {
        repository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    @Test
    void seedsSixPublishedConfigs() {
        List<AssistantConfig> published = repository.findByStatus(ConfigStatus.PUBLISHED);
        assertThat(published).hasSize(6);
        assertThat(published).allSatisfy(c -> {
            assertThat(c.getVersion()).isEqualTo(1);
            assertThat(c.getUpdatedBy()).isEqualTo("system");
            assertThat(c.getCreatedBy()).isEqualTo("system");
            assertThat(c.getContent()).isNotBlank();
        });
        assertThat(published).extracting(AssistantConfig::getConfigKey).containsExactlyInAnyOrder(
                "system.main", "instruction.customer-overview", "instruction.opportunity",
                "instruction.contact-prep", "instruction.contact-topic", "instruction.market-brief");
    }

    @Test
    void seedIsIdempotent() {
        seedLoader.run(new DefaultApplicationArguments());
        assertThat(repository.findByStatus(ConfigStatus.PUBLISHED)).hasSize(6);
        assertThat(repository.count()).isEqualTo(6);
    }
}
