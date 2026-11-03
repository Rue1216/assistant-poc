package com.example.copilot.aiassistant.domain.exception;

import java.util.List;

public class AssistantException extends RuntimeException {

    private final ErrorCode code;
    private final List<ErrorDetail> details;

    public AssistantException(ErrorCode code) {
        this(code, List.of());
    }

    public AssistantException(ErrorCode code, List<? extends ErrorDetail> details) {
        super(code.name());
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
