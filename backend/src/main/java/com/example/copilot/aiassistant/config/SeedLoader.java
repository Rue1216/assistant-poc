package com.example.copilot.aiassistant.config;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Imports the initial prompt configs as published v1 when the collection is empty (04 section 2.7). */
@Component
@Order(2)
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);
    private static final String SYSTEM_USER = "system";

    private final AssistantConfigRepository repository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public SeedLoader(AssistantConfigRepository repository, Clock clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    record ManifestEntry(String configKey, ConfigType configType, String name, String file) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("seed skipped: configs already exist");
            return;
        }
        Instant now = clock.instant();
        List<ManifestEntry> entries = objectMapper.readValue(ClasspathText.read("seed/manifest.json"),
                new TypeReference<List<ManifestEntry>>() { });
        for (ManifestEntry entry : entries) {
            AssistantConfig config = new AssistantConfig();
            config.setConfigKey(entry.configKey());
            config.setConfigType(entry.configType());
            config.setName(entry.name());
            config.setContent(ClasspathText.read("seed/" + entry.file()));
            config.setStatus(ConfigStatus.PUBLISHED);
            config.setVersion(1);
            config.setCreatedBy(SYSTEM_USER);
            config.setCreatedAt(now);
            config.setUpdatedBy(SYSTEM_USER);
            config.setUpdatedAt(now);
            repository.save(config);
        }
        log.info("seed completed: {} configs published", entries.size());
    }
}
