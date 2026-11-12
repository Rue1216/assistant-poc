package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import org.mapstruct.Mapper;

/** Field-for-field mapping; {@code id} and {@code docVersion} travel too so {@code @Version} can guard updates. */
@Mapper(componentModel = "spring")
public interface AssistantConfigDocumentMapper {

    AssistantConfigDocument toDocument(AssistantConfig config);

    AssistantConfig toDomain(AssistantConfigDocument document);

    default String map(ConfigKey key) {
        return key == null ? null : key.value();
    }

    default ConfigKey map(String value) {
        return value == null ? null : new ConfigKey(value);
    }
}
