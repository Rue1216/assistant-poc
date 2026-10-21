package com.example.copilot.aiassistant.config.dto;

import com.example.copilot.aiassistant.config.AssistantConfig;
import com.example.copilot.aiassistant.config.ConfigStatus;
import com.example.copilot.aiassistant.config.ConfigType;
import java.time.OffsetDateTime;
import java.util.List;

/** 02 section 3.4; every field of 04 section 2.1 except the internal id. */
public record ConfigVersionView(String configKey, ConfigType configType, String name, String content,
                                List<String> exampleQuestions, ConfigStatus status, int version,
                                Integer basedOnVersion, String changeNote, String createdBy,
                                OffsetDateTime createdAt, String updatedBy, OffsetDateTime updatedAt,
                                OffsetDateTime scheduledAt, Long docVersion) {

    public static ConfigVersionView of(AssistantConfig c, List<String> exampleQuestions) {
        return new ConfigVersionView(c.getConfigKey(), c.getConfigType(), c.getName(), c.getContent(),
                exampleQuestions, c.getStatus(), c.getVersion(), c.getBasedOnVersion(), c.getChangeNote(),
                c.getCreatedBy(), AdminTime.at(c.getCreatedAt()), c.getUpdatedBy(),
                AdminTime.at(c.getUpdatedAt()), AdminTime.at(c.getScheduledAt()), c.getDocVersion());
    }
}
