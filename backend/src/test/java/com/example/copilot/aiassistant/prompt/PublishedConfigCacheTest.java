package com.example.copilot.aiassistant.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.copilot.aiassistant.config.AssistantConfig;
import com.example.copilot.aiassistant.config.AssistantConfigRepository;
import com.example.copilot.aiassistant.config.ConfigStatus;
import com.example.copilot.aiassistant.config.ConfigType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublishedConfigCacheTest {

    private static AssistantConfig cfg(String key, ConfigType type) {
        AssistantConfig c = new AssistantConfig();
        c.setConfigKey(key);
        c.setConfigType(type);
        return c;
    }

    @Test
    void sortsInstructionsReloadsAfterTtlAndInvalidates() {
        AssistantConfigRepository repo = mock(AssistantConfigRepository.class);
        when(repo.findByStatus(ConfigStatus.PUBLISHED)).thenReturn(List.of(
                cfg("instruction.b", ConfigType.SYSTEM_INSTRUCTION),
                cfg("system.main", ConfigType.SYSTEM_PROMPT),
                cfg("instruction.a", ConfigType.SYSTEM_INSTRUCTION)));
        MutableClock clock = new MutableClock();
        PublishedConfigCache cache = new PublishedConfigCache(repo, clock);

        PublishedConfigCache.PublishedConfigs configs = cache.current();
        assertThat(configs.systemPrompt().getConfigKey()).isEqualTo("system.main");
        assertThat(configs.instructions()).extracting(AssistantConfig::getConfigKey)
                .containsExactly("instruction.a", "instruction.b");

        clock.advance(Duration.ofSeconds(59));
        cache.current();
        verify(repo, times(1)).findByStatus(ConfigStatus.PUBLISHED);

        clock.advance(Duration.ofSeconds(2));
        cache.current();
        verify(repo, times(2)).findByStatus(ConfigStatus.PUBLISHED);

        cache.invalidate();
        cache.current();
        verify(repo, times(3)).findByStatus(ConfigStatus.PUBLISHED);
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
