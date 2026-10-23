package com.example.copilot.aiassistant.audit;

import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.tool.ToolOutcome;
import java.util.List;
import java.util.Map;

/**
 * What the chat flow hands to the audit logger. {@code outcome} is ANSWERED or FAILED. {@code answer} carries the
 * " [n]" markers; {@code citationNumbers} maps a citation id (S1) to its number in the answer.
 */
public record AuditEntry(String requestId, UserContext user, CustomerContext customer, String redactedQuestion,
                         String answer, boolean answerTruncated, String outcome, String errorCode,
                         List<ToolOutcome> toolCalls, Map<String, Integer> citationNumbers,
                         List<String> unverifiedNumbers, long durationMs) {
}
