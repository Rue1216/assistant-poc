package com.example.copilot.aiassistant.port;

import java.time.LocalDate;

public record InteractionRecord(LocalDate date, String channel, String topic, String summary, String outcome,
                                String handledBy) {
}
