package com.example.copilot.aiassistant.audit;

import com.example.copilot.aiassistant.tool.ToolOutcome;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Writes the audit document for one question. Failures never reach the caller (spec 04 section 3.4). */
@Component
public class AuditLogger {

    static final int MAX_QUESTION = 1000;
    static final int MAX_ANSWER = 8000;
    static final int MAX_TOOL_CALLS = 5;
    private static final int MAX_ID = 64;
    private static final int MAX_ROLE = 32;
    private static final int MAX_CUSTOMER = 32;

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final MongoTemplate mongo;
    private final Clock clock;
    private final CredentialMasker masker;

    public AuditLogger(MongoTemplate mongo, Clock clock, CredentialMasker masker) {
        this.mongo = mongo;
        this.clock = clock;
        this.masker = masker;
    }

    public void record(AuditEntry entry) {
        try {
            mongo.insert(toRecord(entry).toDocument(), AuditRecord.COLLECTION);
        } catch (Exception e) {
            // Class name only: messages and the entry may contain question/answer text.
            log.error("Audit write failed requestId={} error={}", entry == null ? null : entry.requestId(),
                    e.getClass().getName());
        }
    }

    private AuditRecord toRecord(AuditEntry e) {
        String question = clip(masker.mask(e.redactedQuestion() == null ? "" : e.redactedQuestion()), MAX_QUESTION);
        String answer = e.answer();
        boolean truncated = answer != null && answer.codePointCount(0, answer.length()) > MAX_ANSWER;
        List<ToolOutcome> tools = e.toolCalls() == null ? List.of() : e.toolCalls();
        List<AuditRecord.ToolCall> calls = tools.stream().limit(MAX_TOOL_CALLS).map(AuditLogger::toCall).toList();
        return new AuditRecord(null, clip(e.requestId(), MAX_ID), clip(e.user().userId(), MAX_ID),
                clip(e.user().roleKey(), MAX_ROLE), clip(e.customer().customerId(), MAX_CUSTOMER),
                e.customer().accessLevel().name(), question, truncated ? clip(answer, MAX_ANSWER) : answer,
                truncated, e.outcome(), clip(e.errorCode(), MAX_ID), calls, clamp(e.durationMs()), clock.instant());
    }

    private static AuditRecord.ToolCall toCall(ToolOutcome o) {
        return new AuditRecord.ToolCall(clip(o.toolName(), MAX_ID), o.result().status().name(),
                clip(o.result().errorCode(), MAX_ID), clamp(o.durationMs()));
    }

    /** Clips to {@code max} code points so a surrogate pair is never split. */
    private static String clip(String s, int max) {
        if (s == null || s.codePointCount(0, s.length()) <= max) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, max));
    }

    private static int clamp(long v) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, v));
    }
}
