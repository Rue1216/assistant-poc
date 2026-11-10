package com.example.copilot.aiassistant.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.application.llm.LlmCallPurpose;
import com.example.copilot.aiassistant.application.llm.LlmErrorMapper;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.testsupport.LogCapture;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.net.http.HttpTimeoutException;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OutboundLlmLogListenerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final OutboundLlmLogListener listener = new OutboundLlmLogListener(new LlmErrorMapper(), mapper);
    private final ChatRequest request = ChatRequest.builder().messages(UserMessage.from("q"))
            .modelName("test-model").build();
    private Map<Object, Object> attrs;

    @BeforeEach
    void setUp() {
        MDC.put("requestId", "r-1");
        attrs = new HashMap<>(Map.of(LlmCallPurpose.class, LlmCallPurpose.ROUTE));
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private ChatResponse response(String modelName, TokenUsage usage) {
        return ChatResponse.builder().aiMessage(AiMessage.from("x")).modelName(modelName).tokenUsage(usage).build();
    }

    private void request() {
        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attrs));
    }

    private void respond(ChatResponse response) {
        listener.onResponse(new ChatModelResponseContext(response, request, ModelProvider.OPEN_AI, attrs));
    }

    private void fail(Throwable error) {
        listener.onError(new ChatModelErrorContext(error, request, ModelProvider.OPEN_AI, attrs));
    }

    private JsonNode json(LogEvent event) {
        return mapper.readTree(event.getMessage().getFormattedMessage());
    }

    private static LogCapture capture() {
        return LogCapture.of(OutboundLlmLogListener.LOGGER_NAME, Level.INFO);
    }

    @Test
    void successWritesOneInfoLineInTableDFormat() {
        try (LogCapture logs = capture()) {
            request();
            respond(response("gpt-x", new TokenUsage(100, 20)));

            assertThat(logs.events()).hasSize(1);
            LogEvent event = logs.events().get(0);
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getThrown()).isNull();
            JsonNode json = json(event);
            assertThat(json.get("event").asString()).isEqualTo("external_call_completed");
            assertThat(json.get("result").asString()).isEqualTo("SUCCESS");
            assertThat(json.at("/request/target_system").asString()).isEqualTo("OpenAI");
            assertThat(json.at("/request/path").asString()).isEqualTo("OpenAiChatModel.chat");
            assertThat(json.at("/response/response_type").asString()).isEqualTo("ChatResponse");
            assertThat(json.at("/response/status_code").asString()).isEqualTo("200");
            assertThat(json.at("/response/result_code").asString()).isEqualTo("SUCCESS");
            assertThat(json.get("duration_ms").isString()).isTrue();
            assertThat(json.get("duration_ms").asString()).matches("\\d+");
            assertThat(json.get("execution_time").asString()).isEqualTo(json.get("duration_ms").asString());
            assertThat(json.at("/llm/request_id").asString()).isEqualTo("r-1");
            assertThat(json.at("/llm/purpose").asString()).isEqualTo("ROUTE");
            assertThat(json.at("/llm/model").asString()).isEqualTo("gpt-x");
            assertThat(json.at("/llm/input_tokens").asString()).isEqualTo("100");
            assertThat(json.at("/llm/output_tokens").asString()).isEqualTo("20");
            assertThat(json.at("/llm/total_tokens").asString()).isEqualTo("120");
            assertAllLeavesAreStrings(json);
        }
    }

    private static void assertAllLeavesAreStrings(JsonNode node) {
        if (node.isObject()) {
            node.forEach(OutboundLlmLogListenerTest::assertAllLeavesAreStrings);
        } else {
            assertThat(node.isString()).as("leaf %s", node).isTrue();
        }
    }

    @Test
    void failureWritesErrorWithStatusCodeAndNoMessages() {
        try (LogCapture logs = capture()) {
            request();
            fail(new RateLimitException(new HttpException(429, "SECRET-BODY")));

            assertThat(logs.events()).hasSize(1);
            LogEvent event = logs.events().get(0);
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            JsonNode json = json(event);
            assertThat(json.get("event").asString()).isEqualTo("external_call_failed");
            assertThat(json.get("result").asString()).isEqualTo("FAIL");
            assertThat(json.at("/response/status_code").asString()).isEqualTo("429");
            assertThat(json.at("/response/response_type").asString()).isEmpty();
            assertThat(json.get("error_type").asString()).isEqualTo("RateLimitException");
            assertThat(json.get("root_cause_type").asString()).isEqualTo("HttpException");
            assertThat(json.at("/llm/error_code").asString()).isEqualTo(ErrorCode.AI_LLM_RATE_LIMITED.name());
            assertThat(json.at("/llm/model").asString()).isEqualTo("test-model");
            assertThat(json.has("error_message")).isFalse();
            assertThat(json.has("root_cause_message")).isFalse();
            assertThat(LogCapture.render(event)).doesNotContain("SECRET-BODY")
                    .contains("Caused by: dev.langchain4j.exception.HttpException");
        }
    }

    @Test
    void timeoutWithoutHttpStatusReports500() {
        try (LogCapture logs = capture()) {
            request();
            fail(new TimeoutException(new HttpTimeoutException("SECRET")));

            LogEvent event = logs.events().get(0);
            JsonNode json = json(event);
            assertThat(json.at("/response/status_code").asString()).isEqualTo("500");
            assertThat(json.at("/llm/error_code").asString()).isEqualTo(ErrorCode.AI_LLM_TIMEOUT.name());
            assertThat(LogCapture.render(event)).doesNotContain("SECRET");
        }
    }

    @Test
    void rateLimitWithoutHttpCauseReports500() {
        try (LogCapture logs = capture()) {
            request();
            fail(new RateLimitException("SECRET"));

            LogEvent event = logs.events().get(0);
            JsonNode json = json(event);
            assertThat(json.at("/response/status_code").asString()).isEqualTo("500");
            assertThat(json.at("/llm/error_code").asString()).isEqualTo(ErrorCode.AI_LLM_RATE_LIMITED.name());
            assertThat(LogCapture.render(event)).doesNotContain("SECRET");
        }
    }

    @Test
    void missingPurposeIsUnknown() {
        attrs.clear();
        try (LogCapture logs = capture()) {
            request();
            respond(response("gpt-x", new TokenUsage(1, 1)));

            assertThat(json(logs.events().get(0)).at("/llm/purpose").asString()).isEqualTo("UNKNOWN");
        }
    }

    @Test
    void missingRequestIdIsBlank() {
        MDC.remove("requestId");
        try (LogCapture logs = capture()) {
            request();
            respond(response("gpt-x", new TokenUsage(1, 1)));

            assertThat(json(logs.events().get(0)).at("/llm/request_id").asString()).isEmpty();
        }
    }

    @Test
    void nullTokenUsageAndModelNameFallBack() {
        try (LogCapture logs = capture()) {
            request();
            respond(response(null, null));

            JsonNode json = json(logs.events().get(0));
            assertThat(json.at("/llm/input_tokens").asString()).isEmpty();
            assertThat(json.at("/llm/output_tokens").asString()).isEmpty();
            assertThat(json.at("/llm/total_tokens").asString()).isEmpty();
            assertThat(json.at("/llm/model").asString()).isEqualTo("test-model");
        }
    }

    @Test
    void missingStartTimeLeavesDurationBlank() {
        try (LogCapture logs = capture()) {
            respond(response("gpt-x", new TokenUsage(1, 1)));

            JsonNode json = json(logs.events().get(0));
            assertThat(json.get("duration_ms").asString()).isEmpty();
            assertThat(json.get("execution_time").asString()).isEmpty();
        }
    }

    @Test
    void logMessageMdcIsRestored() {
        try (LogCapture logs = capture()) {
            MDC.put("logMessage", "outer");
            request();
            respond(response("gpt-x", new TokenUsage(1, 1)));

            String message = logs.events().get(0).getContextData().getValue("logMessage");
            assertThat(message).contains("event=\"external_call_completed\"", "operation=\"ROUTE\"",
                    "status_code=\"200\"");
            assertThat(MDC.get("logMessage")).isEqualTo("outer");

            MDC.remove("logMessage");
            respond(response("gpt-x", new TokenUsage(1, 1)));
            assertThat(MDC.get("logMessage")).isNull();
        }
    }

    @Test
    void internalFailureIsSwallowed() {
        LlmErrorMapper failing = new LlmErrorMapper() {
            @Override
            public ErrorCode map(Throwable t) {
                throw new IllegalStateException("SECRET");
            }
        };
        OutboundLlmLogListener broken = new OutboundLlmLogListener(failing, mapper);
        try (LogCapture logs = LogCapture.of(OutboundLlmLogListener.class, Level.WARN)) {
            broken.onError(new ChatModelErrorContext(new RateLimitException("x"), request, ModelProvider.OPEN_AI,
                    attrs));

            assertThat(logs.events()).hasSize(1);
            LogEvent event = logs.events().get(0);
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(LogCapture.render(event)).contains("IllegalStateException").doesNotContain("SECRET");
        }
    }
}
