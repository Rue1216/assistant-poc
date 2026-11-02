package com.example.copilot.aiassistant.interfaces.rest.config.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Admin API timestamps are ISO 8601 with a +08:00 offset (Asia/Taipei); storage stays UTC. */
public final class AdminTime {

    private static final ZoneOffset TAIPEI = ZoneOffset.ofHours(8);

    private AdminTime() {
    }

    public static OffsetDateTime at(Instant instant) {
        return instant == null ? null : instant.atOffset(TAIPEI);
    }
}
