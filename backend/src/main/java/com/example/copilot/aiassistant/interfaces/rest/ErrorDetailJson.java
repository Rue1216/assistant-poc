package com.example.copilot.aiassistant.interfaces.rest;

import com.example.copilot.aiassistant.domain.exception.ErrorDetail;
import com.example.copilot.aiassistant.domain.exception.WorkingCopyDetail;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.AdminTime;
import java.time.OffsetDateTime;

/** Maps domain error details to their wire shape; times use the admin +08:00 offset. */
final class ErrorDetailJson {

    record WorkingCopy(String status, OffsetDateTime scheduledAt, OffsetDateTime updatedAt, long docVersion) {
    }

    private ErrorDetailJson() {
    }

    static Object of(ErrorDetail d) {
        if (d instanceof WorkingCopyDetail w) {
            return new WorkingCopy(w.status().name(), AdminTime.at(w.scheduledAt()), AdminTime.at(w.updatedAt()),
                    w.docVersion());
        }
        return d;
    }
}
