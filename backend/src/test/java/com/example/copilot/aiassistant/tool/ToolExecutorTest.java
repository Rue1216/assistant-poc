package com.example.copilot.aiassistant.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import com.example.copilot.aiassistant.tool.testsupport.FakeTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ToolExecutorTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ToolContext context = new ToolContext(
            new UserContext("u1", "User", "ROLE", Set.of()),
            new CustomerContext("C001", AccessLevel.FULL, Set.of("C001", "王小明"), Set.of("A123456789")));

    private ToolExecutor executorFor(AssistantTool<?> tool, Duration ceiling) {
        AiAssistantProperties props = new AiAssistantProperties(null,
                new AiAssistantProperties.Limits(1000, 10, 8000, 5, 3, Duration.ofSeconds(60), 16000,
                        ceiling, 4096, 10), null, null);
        ToolGuard guard = new ToolGuard(new ToolRegistry(List.of(tool)),
                Validation.buildDefaultValidatorFactory().getValidator(), mapper);
        return new ToolExecutor(guard, new PiiRedactor(), mapper, props);
    }

    private ToolOutcome run(Duration toolTimeout, Duration ceiling, Supplier<ToolResult> body) {
        FakeTool<NoArgs> tool = new FakeTool<>("probe", NoArgs.class, AccessLevel.LIMITED, toolTimeout,
                (a, c) -> body.get());
        return executorFor(tool, ceiling).execute(
                ToolExecutionRequest.builder().id("1").name("probe").arguments("{}").build(), context,
                Set.of("probe"));
    }

    private ToolOutcome run(Supplier<ToolResult> body) {
        return run(Duration.ofSeconds(2), Duration.ofSeconds(5), body);
    }

    private static ToolResult sleepThenSucceed() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ToolResult.success("late", null);
    }

    @Test
    void successPayloadIsWrappedAsUntrusted() {
        ToolOutcome outcome = run(() -> ToolResult.success(Map.of("price", 100),
                List.of(new Source("TWSE", "u", "t"))));

        JsonNode payload = mapper.readTree(outcome.payloadForModel());
        assertThat(payload.get("untrustedData").asBoolean()).isTrue();
        assertThat(payload.get("status").asString()).isEqualTo("SUCCESS");
        assertThat(payload.get("data").get("price").asInt()).isEqualTo(100);
        assertThat(payload.has("truncated")).isFalse();
        assertThat(outcome.toolName()).isEqualTo("probe");
        assertThat(outcome.purpose()).isEqualTo("測試 probe");
        assertThat(outcome.result().sources()).hasSize(1);
    }

    @Test
    void slowToolTimesOut() {
        long start = System.nanoTime();
        ToolOutcome outcome = run(Duration.ofMillis(200), Duration.ofSeconds(5), ToolExecutorTest::sleepThenSucceed);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(outcome.result().status()).isEqualTo(ToolStatus.TIMEOUT);
        assertThat(outcome.result().errorCode()).isEqualTo("TOOL_TIMEOUT");
        assertThat(outcome.payloadForModel()).contains("\"errorCode\":\"TOOL_TIMEOUT\"").contains("查詢逾時");
        assertThat(elapsedMs).isLessThan(200 + 1000);
    }

    @Test
    void globalCeilingCapsToolTimeout() {
        ToolOutcome outcome = run(Duration.ofSeconds(30), Duration.ofMillis(100), ToolExecutorTest::sleepThenSucceed);
        assertThat(outcome.result().errorCode()).isEqualTo("TOOL_TIMEOUT");
    }

    @Test
    void exceptionBecomesExecutionFailed() {
        ToolOutcome outcome = run(() -> {
            throw new RuntimeException("internal host 10.0.0.5");
        });

        assertThat(outcome.result().status()).isEqualTo(ToolStatus.ERROR);
        assertThat(outcome.result().errorCode()).isEqualTo("TOOL_EXECUTION_FAILED");
        assertThat(outcome.payloadForModel()).doesNotContain("10.0.0.5").contains("查詢失敗");
    }

    @Test
    void guardRejectionIsWrappedToo() {
        FakeTool<NoArgs> tool = new FakeTool<>("probe", NoArgs.class, AccessLevel.LIMITED, Duration.ofSeconds(1),
                (a, c) -> ToolResult.success(null, null));
        ToolOutcome outcome = executorFor(tool, Duration.ofSeconds(5)).execute(
                ToolExecutionRequest.builder().id("1").name("probe").arguments("{\"x\":1}").build(), context,
                Set.of("probe"));
        assertThat(outcome.result().status()).isEqualTo(ToolStatus.DENIED);
        assertThat(outcome.payloadForModel()).contains("\"status\":\"DENIED\"").contains("TOOL_ARGUMENT_INVALID");
    }

    @Test
    void textInDataIsRedacted() {
        ToolOutcome outcome = run(() -> ToolResult.success(
                Map.of("notes", List.of("王小明 來電詢問基金"), "nested", Map.of("who", "王小明")), null));

        assertThat(outcome.payloadForModel()).contains("【本顧客】").doesNotContain("王小明");
    }

    @Test
    void oversizedListIsTruncated() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            rows.add(Map.of("id", "row-" + i, "note", "第 " + i + " 筆互動摘要內容"));
        }
        ToolOutcome outcome = run(() -> ToolResult.success(Map.of("items", rows, "total", 200), null));

        assertThat(outcome.payloadForModel().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(4096);
        assertThat(outcome.payloadForModel()).contains("\"truncated\":true");
        JsonNode payload = mapper.readTree(outcome.payloadForModel());
        assertThat(payload.get("data").get("items").size()).isBetween(1, 199);
        assertThat(payload.get("data").get("items").get(0).get("id").asString()).isEqualTo("row-0");
        assertThat(payload.get("data").get("total").asInt()).isEqualTo(200);
    }

    @Test
    void oversizedWithoutListFailsSafely() {
        ToolOutcome outcome = run(() -> ToolResult.success("x".repeat(5000), null));
        assertThat(outcome.result().errorCode()).isEqualTo("TOOL_EXECUTION_FAILED");
        assertThat(outcome.payloadForModel().length()).isLessThan(4096);
    }

    @Test
    void nullDataAndNullMapValuesAreHandled() {
        ToolOutcome none = run(() -> ToolResult.success(null, List.of(new Source("S", "u", "t"))));
        assertThat(mapper.readTree(none.payloadForModel()).get("status").asString()).isEqualTo("SUCCESS");

        java.util.HashMap<String, Object> withNull = new java.util.HashMap<>();
        withNull.put("a", null);
        withNull.put("b", "x");
        ToolOutcome nulls = run(() -> ToolResult.success(withNull, null));
        JsonNode data = mapper.readTree(nulls.payloadForModel()).get("data");
        assertThat(data.get("b").asString()).isEqualTo("x");
        assertThat(nulls.result().status()).isEqualTo(ToolStatus.SUCCESS);
    }

    @Test
    void rejectedCallDoesNotEchoModelSuppliedName() {
        FakeTool<NoArgs> tool = new FakeTool<>("probe", NoArgs.class, AccessLevel.LIMITED, Duration.ofSeconds(1),
                (a, c) -> ToolResult.success(null, null));
        ToolOutcome outcome = executorFor(tool, Duration.ofSeconds(5)).execute(
                ToolExecutionRequest.builder().id("1").name("x".repeat(500)).arguments("{}").build(), context,
                Set.of("probe"));
        assertThat(outcome.toolName()).hasSize(64);
        assertThat(outcome.purpose()).isEqualTo("未知工具");
    }

    @Test
    void failureLogContainsNoExceptionMessage() {
        Logger logger = (Logger) LoggerFactory.getLogger(ToolExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            run(() -> {
                throw new RuntimeException("王小明 0912-000-111");
            });
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        String output = event.getFormattedMessage() + ThrowableProxyUtil.asString(event.getThrowableProxy());
        assertThat(output).contains("tool=probe").contains("java.lang.RuntimeException")
                .doesNotContain("王小明").doesNotContain("0912");
    }
}
