package com.example.copilot.aiassistant.infrastructure.llm;

import com.example.copilot.aiassistant.application.llm.LlmCallPurpose;
import com.example.copilot.aiassistant.application.llm.LlmErrorMapper;
import com.example.copilot.aiassistant.application.support.RequestContext;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes one platform-format "Outbound" log line per model call. Never logs exception messages, prompts or answers.
 */
@Slf4j
@Component
public class OutboundLlmLogListener implements ChatModelListener {

    static final String LOGGER_NAME = "Outbound";
    static final String LOG_MESSAGE_MDC_KEY = "logMessage";

    private static final Logger OUTBOUND = LoggerFactory.getLogger(LOGGER_NAME);
    private static final Object START_NANOS = new Object();
    private static final String TARGET_SYSTEM = "OpenAI";
    private static final String PATH = "OpenAiChatModel.chat";

    private final LlmErrorMapper errorMapper;
    private final JsonMapper mapper;

    public OutboundLlmLogListener(LlmErrorMapper errorMapper, JsonMapper mapper) {
        this.errorMapper = errorMapper;
        this.mapper = mapper;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put(START_NANOS, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        try {
            String purpose = purpose(context.attributes());
            String duration = duration(context.attributes());
            ObjectNode json = base("external_call_completed", "SUCCESS", "200", "ChatResponse", duration);
            TokenUsage usage = context.chatResponse().tokenUsage();
            String reported = context.chatResponse().modelName();
            ObjectNode llm = llm(json, purpose, reported != null ? reported : context.chatRequest().modelName());
            llm.put("input_tokens", usage == null ? "" : text(usage.inputTokenCount()));
            llm.put("output_tokens", usage == null ? "" : text(usage.outputTokenCount()));
            llm.put("total_tokens", usage == null ? "" : text(usage.totalTokenCount()));
            String line = mapper.writeValueAsString(json);
            withLogMessage(summary("external_call_completed", "SUCCESS", purpose, "200", duration, null, null),
                    () -> OUTBOUND.info(line));
        } catch (RuntimeException e) {
            log.warn("outbound log failed error={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        try {
            Throwable error = context.error();
            String purpose = purpose(context.attributes());
            String duration = duration(context.attributes());
            String status = String.valueOf(statusCode(error));
            String errorType = error.getClass().getSimpleName();
            String rootType = rootCause(error).getClass().getSimpleName();
            ObjectNode json = base("external_call_failed", "FAIL", status, "", duration);
            json.put("error_type", errorType);
            json.put("root_cause_type", rootType);
            llm(json, purpose, context.chatRequest().modelName()).put("error_code", errorMapper.map(error).name());
            String line = mapper.writeValueAsString(json);
            withLogMessage(summary("external_call_failed", "FAIL", purpose, status, duration, errorType, rootType),
                    () -> OUTBOUND.error(line, MessageFreeThrowable.of(error)));
        } catch (RuntimeException e) {
            log.warn("outbound log failed error={}", e.getClass().getSimpleName());
        }
    }

    private ObjectNode base(String event, String result, String status, String responseType, String duration) {
        ObjectNode json = mapper.createObjectNode();
        json.put("event", event);
        json.put("result", result);
        ObjectNode request = json.putObject("request");
        request.put("target_system", TARGET_SYSTEM);
        request.put("path", PATH);
        ObjectNode response = json.putObject("response");
        response.put("response_type", responseType);
        response.put("status_code", status);
        response.put("result_code", result);
        json.put("execution_time", duration);
        json.put("duration_ms", duration);
        return json;
    }

    private ObjectNode llm(ObjectNode json, String purpose, String model) {
        String traceId = MDC.get(RequestContext.MDC_KEY);
        ObjectNode llm = json.putObject("llm");
        llm.put("trace_id", traceId == null ? "" : traceId);
        llm.put("purpose", purpose);
        llm.put("model", model == null ? "" : model);
        return llm;
    }

    private static String purpose(Map<Object, Object> attributes) {
        return LlmCallPurpose.from(attributes).map(Enum::name).orElse("UNKNOWN");
    }

    private static String duration(Map<Object, Object> attributes) {
        if (attributes.get(START_NANOS) instanceof Long start) {
            return String.valueOf((System.nanoTime() - start) / 1_000_000);
        }
        return "";
    }

    private static String text(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** First HTTP status in the cause chain; 500 when there is none (for example a timeout), like the platform. */
    private static int statusCode(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
            if (t instanceof HttpException http) {
                return http.statusCode();
            }
        }
        return 500;
    }

    private static Throwable rootCause(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable root = error;
        for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
            root = t;
        }
        return root;
    }

    private static String summary(String event, String result, String purpose, String status, String duration,
                                  String errorType, String rootType) {
        List<String> parts = new ArrayList<>(List.of(
                pair("event", event), pair("result", result), pair("target_system", TARGET_SYSTEM),
                pair("operation", purpose), pair("status_code", status), pair("duration_ms", duration)));
        if (errorType != null) {
            parts.add(pair("error_type", errorType));
            parts.add(pair("root_cause_type", rootType));
        }
        return String.join(" ", parts);
    }

    private static String pair(String key, String value) {
        return key + "=\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Sets the MDC summary for the platform layout while the line is written, then restores the previous value. */
    private static void withLogMessage(String message, Runnable write) {
        String previous = MDC.get(LOG_MESSAGE_MDC_KEY);
        MDC.put(LOG_MESSAGE_MDC_KEY, message);
        try {
            write.run();
        } finally {
            if (previous == null) {
                MDC.remove(LOG_MESSAGE_MDC_KEY);
            } else {
                MDC.put(LOG_MESSAGE_MDC_KEY, previous);
            }
        }
    }
}
