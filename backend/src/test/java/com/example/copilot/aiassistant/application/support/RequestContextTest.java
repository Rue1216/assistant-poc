package com.example.copilot.aiassistant.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.filter.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class RequestContextTest {

    @Test
    void keysMatchThePlatformFilter() {
        assertThat(RequestContext.MDC_KEY).isEqualTo(TraceIdFilter.MDC_TRACE_ID);
        assertThat(RequestContext.SPAN_KEY).isEqualTo(TraceIdFilter.MDC_SPAN_ID);
    }

    @Test
    void newIdIs32LowerHexAndUnique() {
        assertThat(RequestContext.newId()).matches("[0-9a-f]{32}").isNotEqualTo(RequestContext.newId());
    }

    @Test
    void currentTraceIdReadsTheMdc() {
        MDC.put("traceId", "abc");
        try {
            assertThat(RequestContext.currentTraceId()).isEqualTo("abc");
        } finally {
            MDC.remove("traceId");
        }
    }
}
