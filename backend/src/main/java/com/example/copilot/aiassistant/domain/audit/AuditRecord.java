package com.example.copilot.aiassistant.domain.audit;

import com.example.copilot.aiassistant.domain.config.PromptVersion;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.Document;
import org.springframework.data.annotation.Id;

/**
 * One audit document per question (spec 04 section 3.1). Tool arguments are never stored; a tool result only
 * when {@code ai-assistant.audit.store-tool-results} is on.
 * Written through {@link #toDocument()} so null fields are kept explicitly and no {@code _class} is added.
 */
@org.springframework.data.mongodb.core.mapping.Document(AuditRecord.COLLECTION)
public record AuditRecord(@Id String id, String requestId, String userId, String roleKey,
                          String accessLevel, String question, String answer, boolean answerTruncated,
                          String outcome, String errorCode, List<ToolCall> toolCalls, int durationMs,
                          Instant createdAt, List<String> unverifiedNumbers, int modelCalls,
                          int inputTokens, int outputTokens, int totalTokens,
                          List<PromptVersion> promptVersions, String routeSource,
                          String instructionKey, boolean summaryIn, String summaryStatus,
                          int summarizedMessages, int summaryTokens, String modelName) {

    public static final String COLLECTION = "COLL_ICRM_AI_ASSISTANT_AUDIT";

    public record ToolCall(String toolName, String status, String errorCode, int durationMs, Integer citationId,
                           String target, String dataTime, Integer resultCount, String resultHash,
                           Document result) {
    }

    public Document toDocument() {
        return new Document()
                .append("requestId", requestId)
                .append("userId", userId)
                .append("roleKey", roleKey)
                .append("accessLevel", accessLevel)
                .append("question", question)
                .append("answer", answer)
                .append("answerTruncated", answerTruncated)
                .append("outcome", outcome)
                .append("errorCode", errorCode)
                .append("toolCalls", toolCalls.stream().map(AuditRecord::toolCallDocument)
                        .collect(Collectors.toList()))
                .append("durationMs", durationMs)
                .append("createdAt", Date.from(createdAt))
                .append("unverifiedNumbers", unverifiedNumbers)
                .append("modelCalls", modelCalls)
                .append("inputTokens", inputTokens)
                .append("outputTokens", outputTokens)
                .append("totalTokens", totalTokens)
                .append("promptVersions", promptVersions.stream().map(v -> new Document()
                        .append("configKey", v.configKey()).append("version", v.version()))
                        .collect(Collectors.toList()))
                .append("routeSource", routeSource)
                .append("instructionKey", instructionKey)
                .append("summaryIn", summaryIn)
                .append("summaryStatus", summaryStatus)
                .append("summarizedMessages", summarizedMessages)
                .append("summaryTokens", summaryTokens)
                .append("modelName", modelName)
                // Filled in later by the feedback API; explicit nulls keep the field present.
                .append("feedback", null)
                .append("feedbackAt", null);
    }

    private static Document toolCallDocument(ToolCall c) {
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
            d.append("result", c.result());
        }
        return d;
    }
}
