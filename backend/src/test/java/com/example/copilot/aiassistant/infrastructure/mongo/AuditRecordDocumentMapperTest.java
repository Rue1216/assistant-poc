package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class AuditRecordDocumentMapperTest {

    private final AuditRecordDocumentMapper mapper = Mappers.getMapper(AuditRecordDocumentMapper.class);

    @Test
    void mapsEveryField() {
        Instant now = Instant.parse("2026-10-20T01:00:00Z");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", List.of(Map.of("a", 1), Map.of("b", List.of("x"))));
        result.put("meta", Map.of("total", 2));
        AuditRecord.ToolCall withResult = new AuditRecord.ToolCall("tool", "SUCCESS", "E1", 5, 1, "t", "d", 2,
                "hash", result);
        AuditRecord.ToolCall withoutResult = new AuditRecord.ToolCall("tool2", "DENIED", null, 0, null, null, null,
                null, null, null);
        AuditRecord r = new AuditRecord(null, "0123456789abcdef", "u1", "role", "FULL", "q?", "a", true, "ANSWERED",
                null, List.of(withResult, withoutResult), 10, now, List.of("1"), 1, 2, 3, 5,
                List.of(new PromptVersion("instruction.x", 4)), "ROUTER", "instruction.x", true, "OK", 3, 7, "model");

        AuditRecordDocument d = mapper.toDocument(r);

        assertThat(d.getId()).isNull();
        assertThat(d.getTraceId()).isEqualTo("0123456789abcdef");
        assertThat(d.getUserId()).isEqualTo("u1");
        assertThat(d.getRoleKey()).isEqualTo("role");
        assertThat(d.getAccessLevel()).isEqualTo("FULL");
        assertThat(d.getQuestion()).isEqualTo("q?");
        assertThat(d.getAnswer()).isEqualTo("a");
        assertThat(d.isAnswerTruncated()).isTrue();
        assertThat(d.getOutcome()).isEqualTo("ANSWERED");
        assertThat(d.getErrorCode()).isNull();
        assertThat(d.getDurationMs()).isEqualTo(10);
        assertThat(d.getCreatedAt()).isEqualTo(now);
        assertThat(d.getUnverifiedNumbers()).containsExactly("1");
        assertThat(d.getModelCalls()).isEqualTo(1);
        assertThat(d.getInputTokens()).isEqualTo(2);
        assertThat(d.getOutputTokens()).isEqualTo(3);
        assertThat(d.getTotalTokens()).isEqualTo(5);
        assertThat(d.getPromptVersions()).singleElement().satisfies(v -> {
            assertThat(v.getConfigKey()).isEqualTo("instruction.x");
            assertThat(v.getVersion()).isEqualTo(4);
        });
        assertThat(d.getRouteSource()).isEqualTo("ROUTER");
        assertThat(d.getInstructionKey()).isEqualTo("instruction.x");
        assertThat(d.isSummaryIn()).isTrue();
        assertThat(d.getSummaryStatus()).isEqualTo("OK");
        assertThat(d.getSummarizedMessages()).isEqualTo(3);
        assertThat(d.getSummaryTokens()).isEqualTo(7);
        assertThat(d.getModelName()).isEqualTo("model");
        assertThat(d.getFeedback()).isNull();
        assertThat(d.getFeedbackAt()).isNull();
        assertThat(d.getToolCalls()).hasSize(2);
        AuditRecordDocument.ToolCallDocument c = d.getToolCalls().get(0);
        assertThat(c.getToolName()).isEqualTo("tool");
        assertThat(c.getStatus()).isEqualTo("SUCCESS");
        assertThat(c.getErrorCode()).isEqualTo("E1");
        assertThat(c.getDurationMs()).isEqualTo(5);
        assertThat(c.getCitationId()).isEqualTo(1);
        assertThat(c.getTarget()).isEqualTo("t");
        assertThat(c.getDataTime()).isEqualTo("d");
        assertThat(c.getResultCount()).isEqualTo(2);
        assertThat(c.getResultHash()).isEqualTo("hash");
        assertThat(c.getResult()).isEqualTo(result);
        assertThat(d.getToolCalls().get(1).getResult()).isNull();
    }
}
