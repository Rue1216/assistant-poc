package com.example.copilot.aiassistant.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigQueryServiceTest {

    private final ConfigQueryRepository queries = mock(ConfigQueryRepository.class);
    private final AssistantConfigRepository repository = mock(AssistantConfigRepository.class);
    private final ConfigQueryService service = new ConfigQueryService(queries, repository);

    @Test
    void versionsThrowsNotFoundWhenEmpty() {
        when(queries.versionHistory(new ConfigKey("instruction.nope"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.versions(new ConfigKey("instruction.nope")))
                .isInstanceOfSatisfying(AssistantException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_CONFIG_NOT_FOUND));
    }

    @Test
    void versionThrowsNotFoundWhenMissing() {
        when(repository.findVersion(new ConfigKey("instruction.x"), 3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.version(new ConfigKey("instruction.x"), 3))
                .isInstanceOfSatisfying(AssistantException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_CONFIG_NOT_FOUND));
    }

    @Test
    void versionReturnsRepositoryRecord() {
        AssistantConfig config = AssistantConfig.newDraft(new ConfigKey("instruction.x"), ConfigType.SYSTEM_INSTRUCTION, "X", "body",
                3, "EEE11111", Instant.parse("2026-10-20T02:00:00Z"));
        when(repository.findVersion(new ConfigKey("instruction.x"), 3)).thenReturn(Optional.of(config));

        assertThat(service.version(new ConfigKey("instruction.x"), 3)).isSameAs(config);
    }

    @Test
    void listDelegates() {
        ConfigSummary summary = new ConfigSummary("instruction.x", ConfigType.SYSTEM_INSTRUCTION, "X", null, null);
        when(queries.listSummaries()).thenReturn(List.of(summary));

        assertThat(service.list()).containsExactly(summary);
    }

    @Test
    void versionsReturnsHistoryAsIs() {
        VersionHistoryEntry entry = new VersionHistoryEntry(1, ConfigStatus.PUBLISHED, null, "n", null, "a", "b", null);
        when(queries.versionHistory(new ConfigKey("instruction.x"))).thenReturn(List.of(entry));

        assertThat(service.versions(new ConfigKey("instruction.x"))).containsExactly(entry);
    }
}
