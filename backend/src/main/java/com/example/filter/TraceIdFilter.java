package com.example.filter;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * TraceId MDC Filter
 * 每次 HTTP 請求進入時，從 Header 讀取或自動產生 traceId / spanId
 * 放入 SLF4J MDC 供 Log4j2 使用，並於 response header 回傳 traceId 請求結束後清除 MDC
 * 避免 virtual threads 環境下的 MDC 汙染
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {
    /** 請求 header 中的 trace id 欄位名稱 */
    private static final String HEADER_TRACE_ID = "X-B3-TraceId";
    /** 請求 header 中的 span id 欄位名稱 */
    private static final String HEADER_SPAN_ID = "X-B3-SpanId";
    /** MDC 中的 trace id key */
    public static final String MDC_TRACE_ID = "traceId";
    /** MDC 中的 span id key */
    public static final String MDC_SPAN_ID = "spanId";
    /** MDC 中的 HTTP Method key */
    public static final String MDC_HTTP_METHOD = "httpMethod";
    /** MDC 中的 request URI key */
    public static final String MDC_REQUEST_URI = "requestUri";
    /** 回應 header 中的 trace id 欄位名稱 */
    private static final String RESPONSE_HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 攔截所有 HTTP 請求，注入 traceId / spanId 到 MDC，並於 response header 回傳 traceId
     */
    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            // 讀取或產生 traceId / spanId
            String traceId = extractOrGenerate(httpRequest, HEADER_TRACE_ID);
            String spanId = extractOrGenerate(httpRequest, HEADER_SPAN_ID);

            // 放入 MDC
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_SPAN_ID, spanId);
            MDC.put(MDC_HTTP_METHOD, httpRequest.getMethod());
            MDC.put(MDC_REQUEST_URI, httpRequest.getRequestURI());

            // 回應 header 中加入 traceId
            httpResponse.setHeader(RESPONSE_HEADER_TRACE_ID, traceId);

            // 繼續執行 filter chain
            chain.doFilter(request, response);
        } finally {
            // 清除 MDC，避免 virtual threads 環境下的 MDC 汙染
            MDC.clear();
        }
    }

    /**
     * 從 request header 讀取 traceId / spanId，若不存在則自動產生
     * 
     * @param request
     * @param headerName
     * @return traceId / spanId
     */
    private String extractOrGenerate(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (StringUtils.hasText(value))
            return value;
        return UUID.randomUUID().toString().replace("-", "");
    }

}