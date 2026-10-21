package com.example.copilot.aiassistant.audit;

import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.tool.ToolOutcome;
import java.util.List;

/** What the chat flow hands to the audit logger. {@code outcome} is ANSWERED or FAILED. */
public record AuditEntry(String requestId, UserContext user, CustomerContext customer, String redactedQuestion,
                         String answer, String outcome, String errorCode, List<ToolOutcome> toolCalls,
                         long durationMs) {
}
