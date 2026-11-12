package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class AssistantConfigDocumentMapperTest {

    private final AssistantConfigDocumentMapper mapper = Mappers.getMapper(AssistantConfigDocumentMapper.class);

    @Test
    void roundTripsEveryField() {
        AssistantConfig config = new AssistantConfig("id-1", new ConfigKey("instruction.x"), ConfigType.SYSTEM_INSTRUCTION, "name",
                "content", ConfigStatus.SCHEDULED, 3, 2, "note", "creator", Instant.parse("2026-10-20T01:00:00Z"),
                "editor", Instant.parse("2026-10-20T02:00:00Z"), Instant.parse("2026-10-21T00:00:00Z"), 7L);

        AssistantConfigDocument doc = mapper.toDocument(config);

        assertThat(doc.getId()).isEqualTo("id-1");
        assertThat(doc.getConfigKey()).isEqualTo("instruction.x");
        assertThat(doc.getConfigType()).isEqualTo(ConfigType.SYSTEM_INSTRUCTION);
        assertThat(doc.getName()).isEqualTo("name");
        assertThat(doc.getContent()).isEqualTo("content");
        assertThat(doc.getStatus()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(doc.getVersion()).isEqualTo(3);
        assertThat(doc.getBasedOnVersion()).isEqualTo(2);
        assertThat(doc.getChangeNote()).isEqualTo("note");
        assertThat(doc.getCreatedBy()).isEqualTo("creator");
        assertThat(doc.getCreatedAt()).isEqualTo(Instant.parse("2026-10-20T01:00:00Z"));
        assertThat(doc.getUpdatedBy()).isEqualTo("editor");
        assertThat(doc.getUpdatedAt()).isEqualTo(Instant.parse("2026-10-20T02:00:00Z"));
        assertThat(doc.getScheduledAt()).isEqualTo(Instant.parse("2026-10-21T00:00:00Z"));
        assertThat(doc.getDocVersion()).isEqualTo(7L);
        assertThat(mapper.toDomain(doc)).isEqualTo(config);
    }

    @Test
    void newDraftHasNoIdOrDocVersion() {
        AssistantConfigDocument doc = mapper.toDocument(AssistantConfig.newDraft(new ConfigKey("instruction.x"),
                ConfigType.SYSTEM_INSTRUCTION, "n", "c", 1, "actor", Instant.parse("2026-10-20T01:00:00Z")));

        assertThat(doc.getId()).isNull();
        assertThat(doc.getDocVersion()).isNull();
        assertThat(doc.getBasedOnVersion()).isNull();
        assertThat(doc.getScheduledAt()).isNull();
    }

    @Test
    void mapsConfigKeyValueObjectToAndFromString() {
        assertThat(mapper.map(new ConfigKey("system.main"))).isEqualTo("system.main");
        assertThat(mapper.map("system.main")).isEqualTo(new ConfigKey("system.main"));
        assertThat(mapper.map((ConfigKey) null)).isNull();
        assertThat(mapper.map((String) null)).isNull();
    }
}
