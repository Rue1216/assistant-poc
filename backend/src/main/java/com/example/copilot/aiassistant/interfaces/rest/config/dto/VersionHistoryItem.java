package com.example.copilot.aiassistant.interfaces.rest.config.dto;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import java.time.OffsetDateTime;

/** One row of the version history (02 section 3.3); no content. */
public record VersionHistoryItem(int version, ConfigStatus status, OffsetDateTime scheduledAt, String changeNote,
                                 Integer basedOnVersion, String createdBy, String updatedBy,
                                 OffsetDateTime updatedAt) {

    public static VersionHistoryItem of(AssistantConfig c) {
        return new VersionHistoryItem(c.getVersion(), c.getStatus(), AdminTime.at(c.getScheduledAt()),
                c.getChangeNote(), c.getBasedOnVersion(), c.getCreatedBy(), c.getUpdatedBy(),
                AdminTime.at(c.getUpdatedAt()));
    }
}
