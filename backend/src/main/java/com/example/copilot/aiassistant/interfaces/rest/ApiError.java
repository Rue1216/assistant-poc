package com.example.copilot.aiassistant.interfaces.rest;

import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import java.util.List;

public record ApiError(String code, String message, String requestId, List<Object> details) {

    public static ApiError of(ErrorCode code, List<Object> details) {
        return new ApiError(code.name(), code.message(), RequestIdFilter.current(), details);
    }
}
