package com.example.copilot.aiassistant.application.support;

import java.util.UUID;
import org.slf4j.MDC;

/** Access to the trace id of the request being handled, carried in the logging MDC by the platform filter. */
public final class RequestContext {

    public static final String MDC_KEY = "traceId";
    public static final String SPAN_KEY = "spanId";

    private RequestContext() {
    }

    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }

    /** A fresh id in the platform trace id format: 32 lowercase hex characters. */
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
