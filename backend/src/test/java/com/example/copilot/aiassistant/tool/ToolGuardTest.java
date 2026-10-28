package com.example.copilot.aiassistant.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.tool.testsupport.EchoArgs;
import com.example.copilot.aiassistant.tool.testsupport.FakeTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class ToolGuardTest {

    private final FakeTool<EchoArgs> echo = new FakeTool<>("echoStock", EchoArgs.class, AccessLevel.LIMITED,
            Duration.ofSeconds(1), (a, c) -> ToolResult.success(a, null));
    private final FakeTool<NoArgs> none = new FakeTool<>("ping", NoArgs.class, AccessLevel.LIMITED,
            Duration.ofSeconds(1), (a, c) -> ToolResult.success(null, null));
    // The Boot mapper has FAIL_ON_UNKNOWN_PROPERTIES off; the guard must turn it back on itself.
    private final JsonMapper lenient = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final ToolGuard guard = new ToolGuard(new ToolRegistry(List.of(echo, none)),
            Validation.buildDefaultValidatorFactory().getValidator(), lenient);
    private final Set<String> offered = Set.of("echoStock", "ping");

    private static ToolExecutionRequest call(String name, String args) {
        return ToolExecutionRequest.builder().id("1").name(name).arguments(args).build();
    }

    private static void assertDenied(ToolGuard.GuardResult result, String code, String message) {
        assertThat(result).isInstanceOf(ToolGuard.Rejected.class);
        ToolResult r = ((ToolGuard.Rejected) result).result();
        assertThat(r.status()).isEqualTo(ToolStatus.DENIED);
        assertThat(r.errorCode()).isEqualTo(code);
        assertThat(r.message()).isEqualTo(message);
    }

    @Test
    void unknownToolIsNotAllowed() {
        assertDenied(guard.check(call("deleteCustomer", "{}"), offered), "TOOL_NOT_ALLOWED", "此工具無法使用");
    }

    @Test
    void toolNotOfferedIsNotAllowed() {
        assertDenied(guard.check(call("echoStock", "{\"stockCode\":\"2330\"}"), Set.of("ping")),
                "TOOL_NOT_ALLOWED", "此工具無法使用");
    }

    @Test
    void validArgumentsAreAccepted() {
        ToolGuard.GuardResult result = guard.check(call("echoStock", "{\"stockCode\":\"2330\"}"), offered);
        assertThat(result).isEqualTo(new ToolGuard.Accepted(echo, new EchoArgs("2330")));
    }

    @Test
    void extraFieldIsRejected() {
        assertDenied(guard.check(call("echoStock", "{\"stockCode\":\"2330\",\"certNo\":\"E224567893\"}"), offered),
                "TOOL_ARGUMENT_INVALID", "參數格式不正確");
    }

    @Test
    void invalidPatternIsRejected() {
        assertDenied(guard.check(call("echoStock", "{\"stockCode\":\"abc\"}"), offered),
                "TOOL_ARGUMENT_INVALID", "參數格式不正確");
    }

    @Test
    void malformedJsonIsRejected() {
        assertDenied(guard.check(call("echoStock", "{not json"), offered), "TOOL_ARGUMENT_INVALID", "參數格式不正確");
        assertDenied(guard.check(call("echoStock", "null"), offered), "TOOL_ARGUMENT_INVALID", "參數格式不正確");
    }

    @Test
    void oversizedArgumentsAreRejected() {
        String args = "{" + " ".repeat(1023) + "}";
        assertThat(args.getBytes(StandardCharsets.UTF_8)).hasSize(1025);
        assertDenied(guard.check(call("ping", args), offered), "TOOL_ARGUMENT_INVALID", "參數格式不正確");
        // the same shape at exactly 1024 bytes is accepted, so only the size check can reject the one above
        assertThat(guard.check(call("ping", "{" + " ".repeat(1022) + "}"), offered))
                .isInstanceOf(ToolGuard.Accepted.class);
    }

    @Test
    void nonObjectArgumentsAreRejected() {
        for (String args : List.of("[]", "\"abc\"", "42", "{} {}", "{}x")) {
            assertDenied(guard.check(call("ping", args), offered), "TOOL_ARGUMENT_INVALID", "參數格式不正確");
            assertDenied(guard.check(call("echoStock", args), offered), "TOOL_ARGUMENT_INVALID", "參數格式不正確");
        }
    }

    @Test
    void emptyArgumentsParseAsNoArgs() {
        assertThat(guard.check(call("ping", ""), offered)).isEqualTo(new ToolGuard.Accepted(none, new NoArgs()));
        assertThat(guard.check(call("ping", "{}"), offered)).isEqualTo(new ToolGuard.Accepted(none, new NoArgs()));
        assertThat(guard.check(call("ping", null), offered)).isInstanceOf(ToolGuard.Accepted.class);
    }
}
