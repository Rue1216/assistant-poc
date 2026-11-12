package com.example.copilot.aiassistant.application.config;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** One row of the config list: the live version and the working copy (DRAFT or SCHEDULED), either may be null. */
public record ConfigSummary(String configKey, ConfigType configType, String name,
                            VersionInfo published, VersionInfo workingCopy) {

    /** Summarises all versions of one key; the name is the published one, else the working copy's, else the latest's. */
    public static ConfigSummary of(List<AssistantConfig> versions) {
        AssistantConfig latest = versions.stream().max(Comparator.comparingInt(AssistantConfig::version))
                .orElseThrow();
        AssistantConfig published = versions.stream()
                .filter(c -> c.status() == ConfigStatus.PUBLISHED).findFirst().orElse(null);
        AssistantConfig wc = versions.stream()
                .filter(AssistantConfig::isWorkingCopy).findFirst().orElse(null);
        String name = published != null ? published.name() : wc != null ? wc.name() : latest.name();
        return new ConfigSummary(latest.configKey().value(), latest.configType(), name,
                VersionInfo.of(published), VersionInfo.of(wc));
    }

    /** {@code version} is the internal sequence number; the UI never shows it. */
    public record VersionInfo(int version, ConfigStatus status, Instant scheduledAt,
                              String updatedBy, Instant updatedAt) {

        static VersionInfo of(AssistantConfig c) {
            return c == null ? null : new VersionInfo(c.version(), c.status(), c.scheduledAt(),
                    c.updatedBy(), c.updatedAt());
        }
    }
}
