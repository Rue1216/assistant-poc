package com.example.copilot.aiassistant.domain.audit;

import com.example.copilot.aiassistant.domain.user.UserContext;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import com.example.copilot.aiassistant.domain.tool.ToolOutcome;
import java.util.List;
import java.util.Map;

/**
 * What the chat flow hands to the audit logger. {@code outcome} is ANSWERED or FAILED. {@code answer} carries the
 * " [n]" markers; {@code citationNumbers} maps a citation id (S1) to its number in the answer.
 * The token counts add up every model round, including routing and those of a failed question.
 * {@code routeSource} (ROUTER, CHIP or FALLBACK) and {@code instructionKey} are null when routing never happened.
 * The summary fields are false, null, 0 and 0 when no summary was brought in or made; {@code summaryTokens} is
 * already part of {@code totalTokens}.
 * {@code modelName} is the model the provider reported for the last answer round, or the configured model name
 * when no answer round reported one.
 */
public record AuditEntry(String requestId, UserContext user, CustomerContext customer, String redactedQuestion,
                         String answer, boolean answerTruncated, String outcome, String errorCode,
                         List<ToolOutcome> toolCalls, Map<String, Integer> citationNumbers,
                         List<String> unverifiedNumbers, long durationMs, int modelCalls,
                         long inputTokens, long outputTokens, long totalTokens,
                         List<PromptVersion> promptVersions, String routeSource,
                         String instructionKey,
                         boolean summaryIn, String summaryStatus, int summarizedMessages,
                         long summaryTokens, String modelName) {
}
