package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;

/** Field-for-field mapping; {@code id} and {@code docVersion} travel too so {@code @Version} can guard updates. */
final class AssistantConfigMapper {

    private AssistantConfigMapper() {
    }

    static AssistantConfigDocument toDocument(AssistantConfig c) {
        AssistantConfigDocument d = new AssistantConfigDocument();
        d.setId(c.id());
        d.setConfigKey(c.configKey());
        d.setConfigType(c.configType());
        d.setName(c.name());
        d.setContent(c.content());
        d.setStatus(c.status());
        d.setVersion(c.version());
        d.setBasedOnVersion(c.basedOnVersion());
        d.setChangeNote(c.changeNote());
        d.setCreatedBy(c.createdBy());
        d.setCreatedAt(c.createdAt());
        d.setUpdatedBy(c.updatedBy());
        d.setUpdatedAt(c.updatedAt());
        d.setScheduledAt(c.scheduledAt());
        d.setDocVersion(c.docVersion());
        return d;
    }

    static AssistantConfig toDomain(AssistantConfigDocument d) {
        return new AssistantConfig(d.getId(), d.getConfigKey(), d.getConfigType(), d.getName(), d.getContent(),
                d.getStatus(), d.getVersion(), d.getBasedOnVersion(), d.getChangeNote(), d.getCreatedBy(),
                d.getCreatedAt(), d.getUpdatedBy(), d.getUpdatedAt(), d.getScheduledAt(), d.getDocVersion());
    }
}
