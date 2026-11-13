package com.example.copilot.aiassistant.domain.config;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** How far ahead a publish may be scheduled: at least {@code minLead} and at most {@code maxLead} after now. */
public record ScheduleWindow(Duration minLead, Duration maxLead) {

    /** Both bounds are inclusive; a null or out-of-window {@code at} is {@code AI_INPUT_INVALID(scheduledAt)}. */
    public void requireWithin(Instant at, Instant now) {
        if (at == null || at.isBefore(now.plus(minLead)) || at.isAfter(now.plus(maxLead))) {
            throw new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(new FieldDetail("scheduledAt")));
        }
    }
}
