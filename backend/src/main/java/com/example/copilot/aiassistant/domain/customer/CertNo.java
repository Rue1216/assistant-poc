package com.example.copilot.aiassistant.domain.customer;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import java.util.List;
import java.util.regex.Pattern;

/** A customer certificate number; never printed, so identity values stay out of logs. */
public record CertNo(String value) {

    /** Accepted format. */
    public static final String PATTERN = "^[A-Z0-9&]{8,12}$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    public CertNo {
        if (value == null || !COMPILED.matcher(value).matches()) {
            throw new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(new FieldDetail("certNo")));
        }
    }

    @Override
    public String toString() {
        return "CertNo[***]";
    }
}
