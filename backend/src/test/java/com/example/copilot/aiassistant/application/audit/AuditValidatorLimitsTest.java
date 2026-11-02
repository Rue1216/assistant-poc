package com.example.copilot.aiassistant.application.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.copilot.aiassistant.AiAssistantProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** audit-validator.json repeats AuditLogger's limits; a change to one side must reach the other. */
class AuditValidatorLimitsTest {

    @Test
    void validatorMatchesAuditLoggerLimits() throws IOException {
        Document props;
        try (InputStream in = getClass().getResourceAsStream("/mongo/audit-validator.json")) {
            assertThat(in).isNotNull();
            Document validator = Document.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            props = validator.get("$jsonSchema", Document.class).get("properties", Document.class);
        }

        assertThat(props.get("question", Document.class).getInteger("maxLength")).isEqualTo(AuditLogger.MAX_QUESTION);
        assertThat(props.get("answer", Document.class).getInteger("maxLength")).isEqualTo(AuditLogger.MAX_ANSWER);
        assertThat(props.get("toolCalls", Document.class).getInteger("maxItems")).isEqualTo(AuditLogger.MAX_TOOL_CALLS);
        assertThat(props.get("promptVersions", Document.class).getInteger("maxItems"))
                .isEqualTo(AuditLogger.MAX_PROMPT_VERSIONS);
        assertThat(props.get("unverifiedNumbers", Document.class).getInteger("maxItems"))
                .isEqualTo(AuditLogger.MAX_UNVERIFIED);
    }

    @Test
    void chatLimitsWithinAuditLimitsStart() {
        assertThatCode(() -> logger(AuditLogger.MAX_QUESTION, AuditLogger.MAX_TOOL_CALLS)).doesNotThrowAnyException();
    }

    @Test
    void moreToolCallsThanAuditKeepsFailStartup() {
        assertThatThrownBy(() -> logger(1000, AuditLogger.MAX_TOOL_CALLS + 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("max-tool-calls");
    }

    @Test
    void longerQuestionsThanAuditKeepsFailStartup() {
        assertThatThrownBy(() -> logger(AuditLogger.MAX_QUESTION + 1, 5))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("message-max-chars");
    }

    private static AuditLogger logger(int messageMaxChars, int maxToolCalls) {
        AiAssistantProperties props = new AiAssistantProperties(null,
                new AiAssistantProperties.Limits(messageMaxChars, 10, 8000, maxToolCalls, 3, Duration.ofSeconds(60),
                        16000, Duration.ofSeconds(5), 4096, 10, 1000), null, null, null, null);
        return new AuditLogger(null, Clock.systemUTC(), null, props, null);
    }
}
