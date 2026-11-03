package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bson.Document;

/** Builds the stored audit document; null fields are kept explicitly and no {@code _class} is added. */
final class AuditRecordMapper {

    private AuditRecordMapper() {
    }

    static Document toDocument(AuditRecord r) {
        return new Document()
                .append("requestId", r.requestId())
                .append("userId", r.userId())
                .append("roleKey", r.roleKey())
                .append("accessLevel", r.accessLevel())
                .append("question", r.question())
                .append("answer", r.answer())
                .append("answerTruncated", r.answerTruncated())
                .append("outcome", r.outcome())
                .append("errorCode", r.errorCode())
                .append("toolCalls", r.toolCalls().stream().map(AuditRecordMapper::toolCallDocument)
                        .collect(Collectors.toList()))
                .append("durationMs", r.durationMs())
                .append("createdAt", Date.from(r.createdAt()))
                .append("unverifiedNumbers", r.unverifiedNumbers())
                .append("modelCalls", r.modelCalls())
                .append("inputTokens", r.inputTokens())
                .append("outputTokens", r.outputTokens())
                .append("totalTokens", r.totalTokens())
                .append("promptVersions", r.promptVersions().stream().map(v -> new Document()
                        .append("configKey", v.configKey()).append("version", v.version()))
                        .collect(Collectors.toList()))
                .append("routeSource", r.routeSource())
                .append("instructionKey", r.instructionKey())
                .append("summaryIn", r.summaryIn())
                .append("summaryStatus", r.summaryStatus())
                .append("summarizedMessages", r.summarizedMessages())
                .append("summaryTokens", r.summaryTokens())
                .append("modelName", r.modelName())
                // Filled in later by the feedback API; explicit nulls keep the field present.
                .append("feedback", null)
                .append("feedbackAt", null);
    }

    private static Document toolCallDocument(AuditRecord.ToolCall c) {
        Document d = new Document()
                .append("toolName", c.toolName())
                .append("status", c.status())
                .append("errorCode", c.errorCode())
                .append("durationMs", c.durationMs())
                .append("citationId", c.citationId())
                .append("target", c.target())
                .append("dataTime", c.dataTime())
                .append("resultCount", c.resultCount())
                .append("resultHash", c.resultHash());
        if (c.result() != null) {
            d.append("result", toDocument(c.result()));
        }
        return d;
    }

    /** Nested maps become embedded documents, so they read back as {@link Document}. */
    private static Document toDocument(Map<String, Object> map) {
        Document d = new Document();
        map.forEach((k, v) -> d.append(k, convert(v)));
        return d;
    }

    @SuppressWarnings("unchecked")
    private static Object convert(Object v) {
        if (v instanceof Map<?, ?> m) {
            return toDocument((Map<String, Object>) m);
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            l.forEach(e -> out.add(convert(e)));
            return out;
        }
        return v;
    }
}
