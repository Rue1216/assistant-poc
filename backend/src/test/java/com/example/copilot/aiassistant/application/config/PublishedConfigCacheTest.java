package com.example.copilot.aiassistant.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.copilot.aiassistant.application.command.config.ConfigPublishedEvent;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.PublishedConfigs;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublishedConfigCacheTest {

    private static AssistantConfig cfg(String key, ConfigType type) {
        return new AssistantConfig(null, new ConfigKey(key), type, null, null, null, 0, null, null, null, null, null, null, null,
                null);
    }

    @Test
    void sortsInstructionsReloadsAfterTtlAndInvalidates() {
        AssistantConfigRepository repo = mock(AssistantConfigRepository.class);
        when(repo.findPublished()).thenReturn(List.of(
                cfg("instruction.b", ConfigType.SYSTEM_INSTRUCTION),
                cfg("system.main", ConfigType.SYSTEM_PROMPT),
                cfg("instruction.a", ConfigType.SYSTEM_INSTRUCTION)));
        MutableClock clock = new MutableClock();
        PublishedConfigCache cache = new PublishedConfigCache(repo, clock);

        PublishedConfigs configs = cache.current();
        assertThat(configs.systemPrompt().configKey()).isEqualTo(new ConfigKey("system.main"));
        assertThat(configs.instructions()).extracting(c -> c.configKey().value())
                .containsExactly("instruction.a", "instruction.b");

        clock.advance(Duration.ofSeconds(59));
        cache.current();
        verify(repo, times(1)).findPublished();

        clock.advance(Duration.ofSeconds(2));
        cache.current();
        verify(repo, times(2)).findPublished();

        cache.invalidate();
        cache.current();
        verify(repo, times(3)).findPublished();
    }

    @Test
    void publishedEventDropsTheCachedConfigs() {
        AssistantConfigRepository repo = mock(AssistantConfigRepository.class);
        when(repo.findPublished()).thenReturn(List.of(cfg("system.main", ConfigType.SYSTEM_PROMPT)));
        PublishedConfigCache cache = new PublishedConfigCache(repo, new MutableClock());

        cache.current();
        cache.current();
        verify(repo, times(1)).findPublished();

        cache.onPublished(new ConfigPublishedEvent(new ConfigKey("system.main")));
        cache.current();
        verify(repo, times(2)).findPublished();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
