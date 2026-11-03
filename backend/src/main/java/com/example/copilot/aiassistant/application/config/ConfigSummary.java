package com.example.copilot.aiassistant.application.config;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import java.time.Instant;

/** One row of the config list: the live version and the working copy (DRAFT or SCHEDULED), either may be null. */
public record ConfigSummary(String configKey, ConfigType configType, String name,
                            VersionInfo published, VersionInfo workingCopy) {

    /** {@code version} is the internal sequence number; the UI never shows it. */
    public record VersionInfo(int version, ConfigStatus status, Instant scheduledAt,
                              String updatedBy, Instant updatedAt) {

        static VersionInfo of(AssistantConfig c) {
            return c == null ? null : new VersionInfo(c.version(), c.status(), c.scheduledAt(),
                    c.updatedBy(), c.updatedAt());
        }
    }
}
