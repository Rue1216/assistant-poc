package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

/**
 * Stored form of {@code AssistantConfig}; no {@code _class} is written.
 */
@Getter
@Setter
@Document(AiAssistantCollections.CONFIG)
public class AssistantConfigDocument {

    @Id
    private String id;
    private String configKey;
    private ConfigType configType;
    private String name;
    private String content;
    private ConfigStatus status;
    private int version;
    private Integer basedOnVersion;
    private String changeNote;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private Instant scheduledAt;
    @Version
    private Long docVersion;
}
