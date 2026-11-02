package com.example.copilot.aiassistant.application.support;

import org.slf4j.MDC;

/** Access to the id of the request being handled, carried in the logging MDC. */
public final class RequestContext {

    public static final String MDC_KEY = "requestId";

    private RequestContext() {
    }

    public static String currentRequestId() {
        return MDC.get(MDC_KEY);
    }
}
