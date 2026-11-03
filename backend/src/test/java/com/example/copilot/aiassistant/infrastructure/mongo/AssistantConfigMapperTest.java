package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AssistantConfigMapperTest {

    @Test
    void roundTripsEveryField() {
        AssistantConfig config = new AssistantConfig("id-1", "instruction.x", ConfigType.SYSTEM_INSTRUCTION, "name",
                "content", ConfigStatus.SCHEDULED, 3, 2, "note", "creator", Instant.parse("2026-10-20T01:00:00Z"),
                "editor", Instant.parse("2026-10-20T02:00:00Z"), Instant.parse("2026-10-21T00:00:00Z"), 7L);

        AssistantConfigDocument doc = AssistantConfigMapper.toDocument(config);

        assertThat(doc.getId()).isEqualTo("id-1");
        assertThat(doc.getDocVersion()).isEqualTo(7L);
        assertThat(AssistantConfigMapper.toDomain(doc)).isEqualTo(config);
    }

    @Test
    void newDraftHasNoIdOrDocVersion() {
        AssistantConfigDocument doc = AssistantConfigMapper.toDocument(AssistantConfig.newDraft("instruction.x",
                ConfigType.SYSTEM_INSTRUCTION, "n", "c", 1, "actor", Instant.parse("2026-10-20T01:00:00Z")));

        assertThat(doc.getId()).isNull();
        assertThat(doc.getDocVersion()).isNull();
        assertThat(doc.getBasedOnVersion()).isNull();
        assertThat(doc.getScheduledAt()).isNull();
    }
}
