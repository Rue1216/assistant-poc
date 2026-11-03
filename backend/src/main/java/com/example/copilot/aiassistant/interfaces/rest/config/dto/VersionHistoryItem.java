package com.example.copilot.aiassistant.interfaces.rest.config.dto;

import com.example.copilot.aiassistant.application.config.VersionHistoryEntry;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import java.time.OffsetDateTime;

/** One row of the version history (02 section 3.3); no content. */
public record VersionHistoryItem(int version, ConfigStatus status, OffsetDateTime scheduledAt, String changeNote,
                                 Integer basedOnVersion, String createdBy, String updatedBy,
                                 OffsetDateTime updatedAt) {

    public static VersionHistoryItem of(VersionHistoryEntry c) {
        return new VersionHistoryItem(c.version(), c.status(), AdminTime.at(c.scheduledAt()),
                c.changeNote(), c.basedOnVersion(), c.createdBy(), c.updatedBy(),
                AdminTime.at(c.updatedAt()));
    }
}
