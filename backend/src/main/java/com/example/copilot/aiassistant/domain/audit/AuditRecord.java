package com.example.copilot.aiassistant.domain.audit;

import com.example.copilot.aiassistant.domain.config.PromptVersion;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One audit document per question (spec 04 section 3.1). Tool arguments are never stored; a tool result only
 * when {@code ai-assistant.audit.store-tool-results} is on.
 */
public record AuditRecord(String id, String traceId, String userId, String roleKey,
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
                           Map<String, Object> result) {
    }
}
