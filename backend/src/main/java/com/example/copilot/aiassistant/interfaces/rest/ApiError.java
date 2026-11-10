package com.example.copilot.aiassistant.interfaces.rest;

import com.example.copilot.aiassistant.application.support.RequestContext;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.ErrorDetail;
import java.util.List;

public record ApiError(String code, String message, String traceId, List<Object> details) {

    public static ApiError of(ErrorCode code, List<? extends ErrorDetail> details) {
        List<Object> wire = details.stream().map(ErrorDetailJson::of).toList();
        return new ApiError(code.name(), code.message(), RequestContext.currentTraceId(), wire);
    }
}
