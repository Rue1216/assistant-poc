package com.example.copilot.aiassistant.interfaces.rest.config.dto;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import java.time.OffsetDateTime;
import java.util.List;

/** 02 section 3.4; every field of 04 section 2.1 except the internal id. */
public record ConfigVersionView(String configKey, ConfigType configType, String name, String content,
                                List<String> exampleQuestions, ConfigStatus status, int version,
                                Integer basedOnVersion, String changeNote, String createdBy,
                                OffsetDateTime createdAt, String updatedBy, OffsetDateTime updatedAt,
                                OffsetDateTime scheduledAt, Long docVersion) {

    public static ConfigVersionView of(AssistantConfig c, List<String> exampleQuestions) {
        return new ConfigVersionView(c.configKey().value(), c.configType(), c.name(), c.content(),
                exampleQuestions, c.status(), c.version(), c.basedOnVersion(), c.changeNote(),
                c.createdBy(), AdminTime.at(c.createdAt()), c.updatedBy(),
                AdminTime.at(c.updatedAt()), AdminTime.at(c.scheduledAt()), c.docVersion());
    }
}
