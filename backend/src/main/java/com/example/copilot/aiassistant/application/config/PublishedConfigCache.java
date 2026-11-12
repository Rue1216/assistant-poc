package com.example.copilot.aiassistant.application.config;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.PublishedConfigs;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** In-memory cache of all PUBLISHED configs with a 60 second TTL (04 section 2.6). */
@Component
public class PublishedConfigCache {

    static final Duration TTL = Duration.ofSeconds(60);

    private record Entry(PublishedConfigs configs, Instant loadedAt) {
    }

    private final AssistantConfigRepository repository;
    private final Clock clock;
    private volatile Entry entry;

    public PublishedConfigCache(AssistantConfigRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PublishedConfigs current() {
        Entry cached = entry;
        Instant now = clock.instant();
        if (cached != null && now.isBefore(cached.loadedAt().plus(TTL))) {
            return cached.configs();
        }
        PublishedConfigs loaded = loadFresh();
        entry = new Entry(loaded, now);
        return loaded;
    }

    public void invalidate() {
        entry = null;
    }

    /** Reads the published configs straight from MongoDB without touching the cache. */
    public PublishedConfigs loadFresh() {
        List<AssistantConfig> published = repository.findPublished();
        AssistantConfig systemPrompt = published.stream()
                .filter(c -> c.configType() == ConfigType.SYSTEM_PROMPT)
                .findFirst().orElse(null);
        List<AssistantConfig> instructions = published.stream()
                .filter(c -> c.configType() == ConfigType.SYSTEM_INSTRUCTION)
                .sorted(Comparator.comparing(c -> c.configKey().value()))
                .toList();
        return new PublishedConfigs(systemPrompt, instructions);
    }
}
