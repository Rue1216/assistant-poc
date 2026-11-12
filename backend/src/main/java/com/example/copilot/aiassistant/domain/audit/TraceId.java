package com.example.copilot.aiassistant.domain.audit;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import java.util.List;
import java.util.regex.Pattern;

/** The single tracking id of one chat turn. */
public record TraceId(String value) {

    /** Accepted format. */
    public static final String PATTERN = "^[0-9a-f]{16,32}$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    public TraceId {
        if (value == null || !COMPILED.matcher(value).matches()) {
            throw new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(new FieldDetail("traceId")));
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
