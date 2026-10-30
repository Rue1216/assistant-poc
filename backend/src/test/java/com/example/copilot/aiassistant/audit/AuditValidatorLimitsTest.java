package com.example.copilot.aiassistant.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
}
