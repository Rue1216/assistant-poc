package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

/**
 * Stored form of {@code AssistantConfig}. The type alias pins the {@code _class} value to the pre-refactor class name
 * (the class used to live in the config package), so documents look the same whichever version wrote them.
 */
@Getter
@Setter
@Document("COLL_ICRM_AI_ASSISTANT_CONFIG")
@TypeAlias("com.example.copilot.aiassistant.config.AssistantConfig")
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
