package com.example.copilot.aiassistant.interfaces.rest.config.dto;

import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.application.config.ConfigSummary;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import java.time.OffsetDateTime;

/** One row of the config list (02 section 3.1). */
public record ConfigSummaryView(String configKey, ConfigType configType, String name,
                                VersionInfoView published, VersionInfoView workingCopy) {

    public record VersionInfoView(int version, ConfigStatus status, OffsetDateTime scheduledAt,
                                  String updatedBy, OffsetDateTime updatedAt) {

        static VersionInfoView of(ConfigSummary.VersionInfo v) {
            return v == null ? null : new VersionInfoView(v.version(), v.status(), AdminTime.at(v.scheduledAt()),
                    v.updatedBy(), AdminTime.at(v.updatedAt()));
        }
    }

    public static ConfigSummaryView of(ConfigSummary s) {
        return new ConfigSummaryView(s.configKey(), s.configType(), s.name(),
                VersionInfoView.of(s.published()), VersionInfoView.of(s.workingCopy()));
    }
}
