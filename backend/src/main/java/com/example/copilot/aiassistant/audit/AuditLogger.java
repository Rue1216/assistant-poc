package com.example.copilot.aiassistant.audit;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.privacy.CustomerDeidentifier;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import com.example.copilot.aiassistant.prompt.PromptVersion;
import com.example.copilot.aiassistant.tool.CitationDetail;
import com.example.copilot.aiassistant.tool.Source;
import com.example.copilot.aiassistant.tool.ToolOutcome;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Writes the audit document for one question. Failures never reach the caller (spec 04 section 3.4). */
@Component
public class AuditLogger {

    static final int MAX_QUESTION = 1000;
    static final int MAX_ANSWER = 8000;
    static final int MAX_TOOL_CALLS = 5;
    private static final int MAX_ID = 64;
    private static final int MAX_ROLE = 32;
    private static final int MAX_TARGET = 100;
    private static final int MAX_DATA_TIME = 32;
    static final int MAX_PROMPT_VERSIONS = 100;
    static final int MAX_UNVERIFIED = 20;
    private static final int MAX_UNVERIFIED_LENGTH = 32;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final MongoTemplate mongo;
    private final Clock clock;
    private final CredentialMasker masker;
    private final PiiRedactor redactor;
    private final boolean storeToolResults;

    public AuditLogger(MongoTemplate mongo, Clock clock, CredentialMasker masker, AiAssistantProperties props,
                       PiiRedactor redactor) {
        this.mongo = mongo;
        this.clock = clock;
        this.masker = masker;
        this.redactor = redactor;
        this.storeToolResults = props != null && props.audit() != null && props.audit().storeToolResults();
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
        boolean truncated = e.answerTruncated()
                || answer != null && answer.codePointCount(0, answer.length()) > MAX_ANSWER;
        Map<String, Integer> numbers = e.citationNumbers() == null ? Map.of() : e.citationNumbers();
        List<ToolOutcome> tools = e.toolCalls() == null ? List.of() : e.toolCalls();
        List<AuditRecord.ToolCall> calls = tools.stream().limit(MAX_TOOL_CALLS).map(o -> toCall(o, numbers, e.customer())).toList();
        return new AuditRecord(null, clip(e.requestId(), MAX_ID), clip(e.user().userId(), MAX_ID),
                clip(e.user().roleKey(), MAX_ROLE),
                e.customer().accessLevel().name(), question, truncated ? clip(answer, MAX_ANSWER) : answer,
                truncated, e.outcome(), clip(e.errorCode(), MAX_ID), calls, clamp(e.durationMs()), clock.instant(),
                unverified(e.unverifiedNumbers()), clamp(e.modelCalls()), clamp(e.inputTokens()),
                clamp(e.outputTokens()), clamp(e.totalTokens()), versions(e), clip(e.routeSource(), MAX_ID),
                clip(e.instructionKey(), MAX_ID));
    }

    private AuditRecord.ToolCall toCall(ToolOutcome o, Map<String, Integer> numbers, CustomerContext customer) {
        String toolName = clip(o.toolName(), MAX_ID);
        String status = o.result().status().name();
        String errorCode = clip(o.result().errorCode(), MAX_ID);
        int durationMs = clamp(o.durationMs());
        if (o.citationId() == null) {
            return new AuditRecord.ToolCall(toolName, status, errorCode, durationMs, null, null, null, null, null,
                    null);
        }
        CitationDetail citation = o.result().citation();
        List<Source> sources = o.result().sources();
        return new AuditRecord.ToolCall(toolName, status, errorCode, durationMs, numbers.get(o.citationId()),
                citation == null ? null : clip(redactTarget(citation.target(), customer), MAX_TARGET),
                sources.isEmpty() ? null : clip(sources.get(0).dataTime(), MAX_DATA_TIME),
                citation == null ? null : citation.resultCount(), sha256(o.payloadForModel()),
                storeToolResults ? resultDocument(o.payloadForModel()) : null);
    }

    /** Same order as the answer text: PiiRedactor first, then the self token becomes plain wording. */
    private String redactTarget(String target, CustomerContext customer) {
        if (target == null) {
            return null;
        }
        return redactor.redact(target, customer).replace(PiiRedactor.SELF, CustomerDeidentifier.SUBJECT);
    }

    private static List<PromptVersion> versions(AuditEntry e) {
        List<PromptVersion> all = e.promptVersions() == null ? List.of() : e.promptVersions();
        if (all.size() > MAX_PROMPT_VERSIONS) {
            log.warn("Audit promptVersions truncated requestId={} count={}", e.requestId(), all.size());
            return all.subList(0, MAX_PROMPT_VERSIONS);
        }
        return all;
    }

    private static List<String> unverified(List<String> numbers) {
        return numbers == null ? List.of()
                : numbers.stream().limit(MAX_UNVERIFIED).map(n -> clip(n, MAX_UNVERIFIED_LENGTH)).toList();
    }

    private static String sha256(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The "data" node of the payload sent to the model; null when it cannot be parsed (never logged). */
    private static Document resultDocument(String payload) {
        try {
            JsonNode data = JSON.readTree(payload).get("data");
            if (data == null) {
                return null;
            }
            return Document.parse(data.isObject() ? data.toString() : "{\"value\":" + data + "}");
        } catch (RuntimeException e) {
            return null;
        }
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
