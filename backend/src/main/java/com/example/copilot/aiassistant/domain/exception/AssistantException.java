package com.example.copilot.aiassistant.domain.exception;

import java.util.List;

public class AssistantException extends RuntimeException {

    private final ErrorCode code;
    private final List<Object> details;

    public AssistantException(ErrorCode code) {
        this(code, List.of());
    }

    public AssistantException(ErrorCode code, List<Object> details) {
        super(code.name());
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public List<Object> details() {
        return details;
    }
}
