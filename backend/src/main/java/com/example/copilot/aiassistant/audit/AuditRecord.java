package com.example.copilot.aiassistant.audit;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.Document;
import org.springframework.data.annotation.Id;

/**
 * One audit document per question (spec 04 section 3.1). No tool arguments or results are ever stored.
 * Written through {@link #toDocument()} so null fields are kept explicitly and no {@code _class} is added.
 */
@org.springframework.data.mongodb.core.mapping.Document(AuditRecord.COLLECTION)
public record AuditRecord(@Id String id, String requestId, String userId, String roleKey, String customerId,
                          String accessLevel, String question, String answer, boolean answerTruncated,
                          String outcome, String errorCode, List<ToolCall> toolCalls, int durationMs,
                          Instant createdAt) {

    public static final String COLLECTION = "COLL_ICRM_AI_ASSISTANT_AUDIT";

    public record ToolCall(String toolName, String status, String errorCode, int durationMs) {
    }

    Document toDocument() {
        return new Document()
                .append("requestId", requestId)
                .append("userId", userId)
                .append("roleKey", roleKey)
                .append("customerId", customerId)
                .append("accessLevel", accessLevel)
                .append("question", question)
                .append("answer", answer)
                .append("answerTruncated", answerTruncated)
                .append("outcome", outcome)
                .append("errorCode", errorCode)
                .append("toolCalls", toolCalls.stream().map(c -> new Document()
                        .append("toolName", c.toolName())
                        .append("status", c.status())
                        .append("errorCode", c.errorCode())
                        .append("durationMs", c.durationMs())).collect(Collectors.toList()))
                .append("durationMs", durationMs)
                .append("createdAt", Date.from(createdAt));
    }
}
