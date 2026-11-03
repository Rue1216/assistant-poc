package com.example.copilot.aiassistant.application.config;

import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import java.time.Instant;

/** One row of a key's version history; carries no content. */
public record VersionHistoryEntry(int version, ConfigStatus status, Instant scheduledAt, String changeNote,
                                  Integer basedOnVersion, String createdBy, String updatedBy, Instant updatedAt) {
}
