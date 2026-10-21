package com.example.copilot.aiassistant.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.tool.ToolOutcome;
import com.example.copilot.aiassistant.tool.ToolResult;
import com.example.copilot.aiassistant.tool.ToolStatus;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

class AuditLoggerTest extends IntegrationTestBase {

    private static final String COLL = "COLL_ICRM_AI_ASSISTANT_AUDIT";
    private static final Set<String> DOC_KEYS = Set.of("_id", "requestId", "userId", "roleKey", "customerId",
            "accessLevel", "question", "answer", "answerTruncated", "outcome", "errorCode", "toolCalls",
            "durationMs", "createdAt");

    @Autowired AuditLogger auditLogger;
    @Autowired MongoTemplate mongo;

    private static final UserContext USER = new UserContext("fc.officer", "Officer", "FC_OFFICER", Set.of());
    private static final CustomerContext CUSTOMER =
            new CustomerContext("C001", AccessLevel.FULL, Set.of("王小明"), Set.of("A123456789"));

    private static ToolOutcome tool(String name, ToolStatus status, String code, long ms) {
        ToolResult result = status == ToolStatus.SUCCESS
                ? ToolResult.success(java.util.Map.of("secret", "RESULT-DATA"), List.of())
                : ToolResult.failure(status, code, "msg");
        return new ToolOutcome(name, "PURPOSE-TEXT", result, "PAYLOAD-FOR-MODEL", ms);
    }

    private AuditEntry entry(String requestId, String question, String answer, List<ToolOutcome> tools) {
        return new AuditEntry(requestId, USER, CUSTOMER, question, answer, "ANSWERED", null, tools, 2840);
    }

    private Document find(String requestId) {
        return mongo.getCollection(COLL).find(new Document("requestId", requestId)).first();
    }

    @Test
    void writesOneDocumentPerQuestion() {
        auditLogger.record(entry("req-write-1", "【本顧客】最近有什麼互動？", "### 近期互動",
                List.of(tool("getRecentInteractions", ToolStatus.SUCCESS, null, 12),
                        tool("getCampaignResults", ToolStatus.ERROR, "AI_TOOL_ERROR", 9))));

        assertThat(mongo.count(new Query(Criteria.where("requestId").is("req-write-1")), COLL)).isEqualTo(1);
        Document doc = find("req-write-1");
        assertThat(doc.getString("userId")).isEqualTo("fc.officer");
        assertThat(doc.getString("roleKey")).isEqualTo("FC_OFFICER");
        assertThat(doc.getString("customerId")).isEqualTo("C001");
        assertThat(doc.getString("accessLevel")).isEqualTo("FULL");
        assertThat(doc.getString("question")).isEqualTo("【本顧客】最近有什麼互動？");
        assertThat(doc.getString("answer")).isEqualTo("### 近期互動");
        assertThat(doc.getBoolean("answerTruncated")).isFalse();
        assertThat(doc.getString("outcome")).isEqualTo("ANSWERED");
        assertThat(doc.get("errorCode")).isNull();
        assertThat(doc.getList("toolCalls", Document.class)).hasSize(2);
        assertThat(doc.get("durationMs")).isEqualTo(2840);
        assertThat(doc.get("createdAt")).isInstanceOf(java.util.Date.class);
        assertThat(doc.keySet()).containsExactlyInAnyOrderElementsOf(DOC_KEYS);
    }

    @Test
    void toolCallsExcludeArgumentsAndResults() {
        auditLogger.record(entry("req-tools-1", "q", "a",
                List.of(tool("getRecentInteractions", ToolStatus.SUCCESS, null, 12),
                        tool("getCampaignResults", ToolStatus.DENIED, "AI_TOOL_DENIED", 3))));

        Document doc = find("req-tools-1");
        for (Document call : doc.getList("toolCalls", Document.class)) {
            assertThat(call.keySet()).containsExactlyInAnyOrder("toolName", "status", "errorCode", "durationMs");
            assertThat(call.get("durationMs")).isInstanceOf(Integer.class);
        }
        assertThat(doc.toJson()).doesNotContain("RESULT-DATA", "PAYLOAD-FOR-MODEL", "PURPOSE-TEXT", "王小明",
                "A123456789");
        Document second = doc.getList("toolCalls", Document.class).get(1);
        assertThat(second.getString("status")).isEqualTo("DENIED");
        assertThat(second.getString("errorCode")).isEqualTo("AI_TOOL_DENIED");
    }

    @Test
    void toolCallsAreCappedAtFive() {
        ToolOutcome t = tool("getRecentInteractions", ToolStatus.SUCCESS, null, 1);
        auditLogger.record(entry("req-cap-1", "q", "a", List.of(t, t, t, t, t, t, t)));
        assertThat(find("req-cap-1").getList("toolCalls", Document.class)).hasSize(5);
    }

    @Test
    void credentialsInQuestionAreMasked() {
        auditLogger.record(entry("req-mask-1", "我的 key 是 sk-abcdefghijklmnop1234，密碼：hunter2", "a", List.of()));
        String q = find("req-mask-1").getString("question");
        assertThat(q).contains("[REDACTED]").doesNotContain("sk-abcdefghijklmnop1234", "hunter2");
    }

    @Test
    void maskerReplacesBearerAndPasswordPatterns() {
        CredentialMasker masker = new CredentialMasker();
        assertThat(masker.mask("Bearer abcdefghijklmnop12345")).isEqualTo("[REDACTED]");
        assertThat(masker.mask("password = x1y2z3")).isEqualTo("[REDACTED]");
        assertThat(masker.mask("密碼：hunter2，請幫我分析這位顧客")).isEqualTo("[REDACTED]，請幫我分析這位顧客");
        assertThat(masker.mask("password: p@ss!w0rd.x 然後幫我查")).isEqualTo("[REDACTED] 然後幫我查");
        assertThat(masker.mask("密碼：小明生日1234，幫我查")).isEqualTo("[REDACTED]，幫我查");
        assertThat(masker.mask("password: abc中文def")).isEqualTo("[REDACTED]");
        // Over-masking accepted: no fullwidth punctuation or space ends the value, so the trailing text goes too.
        assertThat(masker.mask("密碼：hunter2請幫我分析")).isEqualTo("[REDACTED]");
        assertThat(masker.mask("普通的問題")).isEqualTo("普通的問題");
        assertThat(masker.mask(null)).isNull();
    }

    @Test
    void longQuestionIsClampedTo1000() {
        auditLogger.record(entry("req-q-1", "問".repeat(1500), "a", List.of()));
        assertThat(find("req-q-1").getString("question")).hasSize(1000);
    }

    @Test
    void longAnswerIsTruncated() {
        auditLogger.record(entry("req-long-1", "q", "答".repeat(9000), List.of()));
        Document doc = find("req-long-1");
        assertThat(doc.getString("answer")).hasSize(8000);
        assertThat(doc.getBoolean("answerTruncated")).isTrue();
    }

    @Test
    void answerTruncationCountsCodePoints() {
        auditLogger.record(entry("req-cp-1", "q", "😀".repeat(8001), List.of()));
        Document over = find("req-cp-1");
        assertThat(over.getString("answer").codePointCount(0, over.getString("answer").length())).isEqualTo(8000);
        assertThat(over.getBoolean("answerTruncated")).isTrue();

        auditLogger.record(entry("req-cp-2", "q", "😀".repeat(8000), List.of()));
        Document exact = find("req-cp-2");
        assertThat(exact.getString("answer")).hasSize(16000);
        assertThat(exact.getBoolean("answerTruncated")).isFalse();
    }

    @Test
    void questionClipCountsCodePoints() {
        auditLogger.record(entry("req-cp-3", "😀".repeat(1001), "a", List.of()));
        String q = find("req-cp-3").getString("question");
        assertThat(q.codePointCount(0, q.length())).isEqualTo(1000);
        auditLogger.record(entry("req-cp-4", "😀".repeat(1000), "a", List.of()));
        assertThat(find("req-cp-4").getString("question")).hasSize(2000);
    }

    @Test
    void overlongIdentifiersAreClippedAndNullsTolerated() {
        ToolOutcome t = tool("n".repeat(100), ToolStatus.ERROR, "E".repeat(100), 1);
        auditLogger.record(new AuditEntry("r".repeat(100), USER, CUSTOMER, null, "a", "FAILED", "X".repeat(100),
                List.of(t), 5));
        Document doc = mongo.getCollection(COLL).find(new Document("requestId", "r".repeat(64))).first();
        assertThat(doc).isNotNull();
        assertThat(doc.getString("question")).isEmpty();
        Document call = doc.getList("toolCalls", Document.class).get(0);
        assertThat(call.getString("toolName")).hasSize(64);
        assertThat(call.getString("errorCode")).hasSize(64);
        assertThat(doc.getString("errorCode")).hasSize(64);
    }

    @Test
    void overlongUserRoleAndCustomerAreClipped() {
        UserContext user = new UserContext("u".repeat(100), "Officer", "R".repeat(50), Set.of());
        CustomerContext customer = new CustomerContext("C".repeat(50), AccessLevel.LIMITED, Set.of(), Set.of());
        auditLogger.record(new AuditEntry("req-clip-1", user, customer, "q", "a", "ANSWERED", null, List.of(), 1));
        Document doc = find("req-clip-1");
        assertThat(doc.getString("userId")).hasSize(64);
        assertThat(doc.getString("roleKey")).hasSize(32);
        assertThat(doc.getString("customerId")).hasSize(32);
    }

    @Test
    void failedEntryStoresNullAnswerAndErrorCode() {
        auditLogger.record(new AuditEntry("req-fail-1", USER, CUSTOMER, "q", null, "FAILED", "AI_LLM_TIMEOUT",
                List.of(), 100));
        Document doc = find("req-fail-1");
        assertThat(doc.get("answer")).isNull();
        assertThat(doc.getBoolean("answerTruncated")).isFalse();
        assertThat(doc.getString("outcome")).isEqualTo("FAILED");
        assertThat(doc.getString("errorCode")).isEqualTo("AI_LLM_TIMEOUT");
    }

    @Test
    void writeFailureDoesNotThrow() {
        MongoTemplate broken = mock(MongoTemplate.class);
        when(broken.insert(any(Document.class), anyString())).thenThrow(new IllegalStateException("db down: secret"));
        AuditLogger logger = new AuditLogger(broken, Clock.systemUTC(), new CredentialMasker());

        ch.qos.logback.classic.Logger target =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AuditLogger.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        target.addAppender(appender);
        try {
            assertThatCode(() -> logger.record(entry("req-err-1", "q", "a", List.of()))).doesNotThrowAnyException();
        } finally {
            target.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("req-err-1", "java.lang.IllegalStateException")
                .doesNotContain("db down", "secret");
    }
}
