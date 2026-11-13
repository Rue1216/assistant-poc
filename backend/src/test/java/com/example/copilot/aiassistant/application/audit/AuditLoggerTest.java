package com.example.copilot.aiassistant.application.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.domain.audit.AuditEntry;
import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import com.example.copilot.aiassistant.domain.audit.CredentialMasker;
import com.example.copilot.aiassistant.domain.customer.CertNo;
import com.example.copilot.aiassistant.domain.repository.AuditRepository;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.user.UserContext;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import com.example.copilot.aiassistant.domain.tool.CitationDetail;
import com.example.copilot.aiassistant.domain.tool.Source;
import com.example.copilot.aiassistant.domain.tool.ToolOutcome;
import com.example.copilot.aiassistant.domain.tool.ToolResult;
import com.example.copilot.aiassistant.domain.tool.ToolStatus;
import com.example.copilot.aiassistant.infrastructure.mongo.AiAssistantCollections;
import com.example.copilot.testsupport.LogCapture;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

class AuditLoggerTest extends IntegrationTestBase {

    private static final String COLL = AiAssistantCollections.AUDIT;
    private static final Set<String> DOC_KEYS = Set.of("_id", "traceId", "userId", "roleKey",
            "accessLevel", "question", "answer", "answerTruncated", "outcome", "errorCode", "toolCalls",
            "durationMs", "createdAt", "unverifiedNumbers", "modelCalls", "inputTokens", "outputTokens",
            "totalTokens", "promptVersions", "routeSource", "instructionKey", "summaryIn", "summaryStatus",
            "summarizedMessages", "summaryTokens", "modelName", "feedback", "feedbackAt");

    @Autowired AuditLogger auditLogger;
    @Autowired MongoTemplate mongo;
    @Autowired AuditRepository repository;

    private static final UserContext USER = new UserContext("ICRM00028", "Officer", "RM", Set.of());
    private static final CustomerContext CUSTOMER =
            new CustomerContext(new CertNo(TestCustomers.RETIREE), AccessLevel.FULL, Set.of("王小明", TestCustomers.RETIREE), Set.of());

    private static ToolOutcome tool(String name, ToolStatus status, String code, long ms) {
        ToolResult result = status == ToolStatus.SUCCESS
                ? ToolResult.success(java.util.Map.of("secret", "RESULT-DATA"), List.of())
                : ToolResult.failure(status, code, "msg");
        return new ToolOutcome(name, "PURPOSE-TEXT", result, "PAYLOAD-FOR-MODEL", ms,
                status == ToolStatus.SUCCESS ? "S1" : null);
    }

    private static AiAssistantProperties propsWith(boolean storeToolResults) {
        return new AiAssistantProperties(null, null, null, null, new AiAssistantProperties.Audit(storeToolResults), null);
    }

    private static String sha256Hex(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String PAYLOAD = "{\"untrustedData\":true,\"status\":\"SUCCESS\",\"citationId\":\"S1\","
            + "\"data\":{\"close\":2420}}";

    private static ToolOutcome citedTool() {
        ToolResult result = ToolResult.success(java.util.Map.of("close", 2420),
                List.of(new Source("TWSE", "https://openapi.twse.com.tw", "2026-10-16")),
                new CitationDetail("上市個股行情", "台積電 2330", "收盤 2420", null, 1));
        return new ToolOutcome("getStockQuote", "PURPOSE-TEXT", result, PAYLOAD, 7, "S1");
    }

    private AuditEntry citedEntry(String traceId, boolean truncated, String answer, List<String> unverified) {
        return new AuditEntry(traceId, USER, CUSTOMER, "q", answer, truncated, "ANSWERED", null,
                List.of(citedTool()), java.util.Map.of("S1", 1), unverified, 10, 1, 0, 0, 0, List.of(), null, null, false, null, 0, 0, null);
    }

    private AuditEntry entry(String traceId, String question, String answer, List<ToolOutcome> tools) {
        return new AuditEntry(traceId, USER, CUSTOMER, question, answer, false, "ANSWERED", null, tools,
                java.util.Map.of(), List.of(), 2840, 1, 0, 0, 0, List.of(), null, null, false, null, 0, 0, null);
    }

    private Document find(String traceId) {
        return mongo.getCollection(COLL).find(new Document("traceId", traceId)).first();
    }

    @Test
    void documentHasTraceIdAndNoRequestId() {
        auditLogger.record(entry("0af7651916cd43dd8448eb211c80319c", "q", "a", List.of()));
        Document doc = find("0af7651916cd43dd8448eb211c80319c");
        assertThat(doc.getString("traceId")).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(doc.containsKey("requestId")).isFalse();
    }

    @Test
    void overlongTraceIdIsClippedTo64AndStillWritten() {
        auditLogger.record(entry("t".repeat(200), "q", "a", List.of()));
        assertThat(find("t".repeat(64))).isNotNull();
    }

    @Test
    void writesOneDocumentPerQuestion() {
        auditLogger.record(entry("req-write-1", "【本顧客】最近有什麼互動？", "### 近期互動",
                List.of(tool("getCustomerNotes", ToolStatus.SUCCESS, null, 12),
                        tool("getCustomerCampaigns", ToolStatus.ERROR, "AI_TOOL_ERROR", 9))));

        assertThat(mongo.count(new Query(Criteria.where("traceId").is("req-write-1")), COLL)).isEqualTo(1);
        Document doc = find("req-write-1");
        assertThat(doc.getString("userId")).isEqualTo("ICRM00028");
        assertThat(doc.getString("roleKey")).isEqualTo("RM");
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
    void auditHasNoCertNo() {
        auditLogger.record(entry("req-nocert-1", "q", "a", List.of()));

        Document doc = find("req-nocert-1");
        assertThat(doc.keySet()).doesNotContain("certNo");
        assertThat(doc.toJson()).doesNotContain(TestCustomers.RETIREE);
    }

    @Test
    void toolCallsExcludeArgumentsAndResults() {
        auditLogger.record(entry("req-tools-1", "q", "a",
                List.of(tool("getCustomerNotes", ToolStatus.SUCCESS, null, 12),
                        tool("getCustomerCampaigns", ToolStatus.DENIED, "AI_TOOL_DENIED", 3))));

        Document doc = find("req-tools-1");
        for (Document call : doc.getList("toolCalls", Document.class)) {
            assertThat(call.keySet()).containsExactlyInAnyOrder("toolName", "status", "errorCode", "durationMs",
                    "citationId", "target", "dataTime", "resultCount", "resultHash");
            assertThat(call.get("durationMs")).isInstanceOf(Integer.class);
        }
        assertThat(doc.toJson()).doesNotContain("RESULT-DATA", "PAYLOAD-FOR-MODEL", "PURPOSE-TEXT", "王小明",
                TestCustomers.RETIREE);
        Document second = doc.getList("toolCalls", Document.class).get(1);
        assertThat(second.getString("status")).isEqualTo("DENIED");
        assertThat(second.getString("errorCode")).isEqualTo("AI_TOOL_DENIED");
    }

    @Test
    void successfulCallsCarryCitationFields() throws Exception {
        auditLogger.record(citedEntry("req-cite-1", false, "a [1]", List.of("3000")));
        Document doc = find("req-cite-1");
        Document call = doc.getList("toolCalls", Document.class).get(0);
        assertThat(call.getInteger("citationId")).isEqualTo(1);
        assertThat(call.getString("target")).isEqualTo("台積電 2330");
        assertThat(call.getString("dataTime")).isEqualTo("2026-10-16");
        assertThat(call.getInteger("resultCount")).isEqualTo(1);
        assertThat(call.getString("resultHash")).isEqualTo(sha256Hex(PAYLOAD)).hasSize(64);
        assertThat(call.containsKey("result")).isFalse();
        assertThat(doc.getList("unverifiedNumbers", String.class)).containsExactly("3000");
        assertThat(doc.toJson()).doesNotContain("2420");
    }

    @Test
    void citationTargetIsRedacted() {
        ToolResult result = ToolResult.success(java.util.Map.of("close", 1), List.of(),
                new CitationDetail("t", "王小明 0912-000-111", "e", null, 1));
        ToolOutcome o = new ToolOutcome("getStockQuote", "p", result, PAYLOAD, 7, "S1");
        auditLogger.record(new AuditEntry("req-cite-7", USER, CUSTOMER, "q", "a", false, "ANSWERED", null,
                List.of(o), java.util.Map.of("S1", 1), List.of(), 10, 1, 0, 0, 0, List.of(), null, null, false, null, 0, 0, null));
        String target = find("req-cite-7").getList("toolCalls", Document.class).get(0).getString("target");
        assertThat(target).doesNotContain("王小明", "0912", "【本顧客】").contains("本顧客");
    }

    @Test
    void failedCallsHaveNullCitationFields() {
        auditLogger.record(entry("req-cite-2", "q", "a",
                List.of(tool("getCustomerCampaigns", ToolStatus.ERROR, "AI_TOOL_ERROR", 9))));
        Document call = find("req-cite-2").getList("toolCalls", Document.class).get(0);
        for (String key : List.of("citationId", "target", "dataTime", "resultCount", "resultHash")) {
            assertThat(call.containsKey(key)).isTrue();
            assertThat(call.get(key)).isNull();
        }
    }

    @Test
    void toolResultStoredOnlyWhenEnabled() {
        AuditLogger enabled = new AuditLogger(repository, Clock.systemUTC(), new CredentialMasker(), propsWith(true), new PiiRedactor());
        enabled.record(citedEntry("req-cite-3", false, "a", List.of()));
        Document call = find("req-cite-3").getList("toolCalls", Document.class).get(0);
        assertThat(call.get("result", Document.class).getInteger("close")).isEqualTo(2420);

        auditLogger.record(citedEntry("req-cite-4", false, "a", List.of()));
        assertThat(find("req-cite-4").getList("toolCalls", Document.class).get(0).containsKey("result")).isFalse();
    }

    @Test
    void bigIntegerToolResultIsOmittedButRecordIsWritten() {
        String payload = "{\"untrustedData\":true,\"status\":\"SUCCESS\",\"citationId\":\"S1\","
                + "\"data\":{\"big\":12345678901234567890123,\"close\":2420}}";
        ToolResult result = ToolResult.success(java.util.Map.of("close", 2420),
                List.of(new Source("TWSE", "https://openapi.twse.com.tw", "2026-10-16")),
                new CitationDetail("t", "台積電 2330", "e", null, 1));
        ToolOutcome o = new ToolOutcome("getStockQuote", "p", result, payload, 7, "S1");
        AuditLogger enabled = new AuditLogger(repository, Clock.systemUTC(), new CredentialMasker(), propsWith(true), new PiiRedactor());
        enabled.record(new AuditEntry("req-big-1", USER, CUSTOMER, "q", "a", false, "ANSWERED", null,
                List.of(o), java.util.Map.of("S1", 1), List.of(), 10, 1, 0, 0, 0, List.of(), null, null, false, null, 0, 0, null));
        Document doc = find("req-big-1");
        assertThat(doc).isNotNull();
        assertThat(doc.getList("toolCalls", Document.class).get(0).containsKey("result")).isFalse();
    }

    @Test
    void toolResultWithUnstorableMapKeyIsOmittedButRecordIsWritten() {
        List<String> payloads = List.of(
                "{\"data\":{\"a.b\":1,\"close\":2420}}",
                "{\"data\":{\"rows\":[{\"ok\":1,\"$bad\":2}],\"close\":2420}}",
                "{\"data\":{\"outer\":{\"inner\":{\"x.y\":1}}}}");
        AuditLogger enabled = new AuditLogger(repository, Clock.systemUTC(), new CredentialMasker(), propsWith(true), new PiiRedactor());
        for (int i = 0; i < payloads.size(); i++) {
            ToolResult result = ToolResult.success(java.util.Map.of("close", 2420),
                    List.of(new Source("TWSE", "https://openapi.twse.com.tw", "2026-10-16")),
                    new CitationDetail("t", "台積電 2330", "e", null, 1));
            ToolOutcome o = new ToolOutcome("getStockQuote", "p", result, payloads.get(i), 7, "S1");
            String id = "req-key-" + i;
            enabled.record(new AuditEntry(id, USER, CUSTOMER, "q", "a", false, "ANSWERED", null,
                    List.of(o), java.util.Map.of("S1", 1), List.of(), 10, 1, 0, 0, 0, List.of(), null, null, false, null, 0, 0, null));
            Document doc = find(id);
            assertThat(doc).isNotNull();
            Document call = doc.getList("toolCalls", Document.class).get(0);
            assertThat(call.getString("toolName")).isEqualTo("getStockQuote");
            assertThat(call.containsKey("result")).isFalse();
        }
    }

    @Test
    void answerTruncatedFromAssemblerIsKept() {
        auditLogger.record(citedEntry("req-cite-5", true, "short", List.of()));
        assertThat(find("req-cite-5").getBoolean("answerTruncated")).isTrue();
    }

    @Test
    void unverifiedNumbersCappedAtTwenty() {
        List<String> many = java.util.stream.IntStream.range(0, 25).mapToObj(i -> "n" + (100 + i)).toList();
        auditLogger.record(citedEntry("req-cite-6", false, "a", many));
        assertThat(find("req-cite-6").getList("unverifiedNumbers", String.class)).hasSize(20);
    }

    @Test
    void toolCallsAreCappedAtFive() {
        ToolOutcome t = tool("getCustomerNotes", ToolStatus.SUCCESS, null, 1);
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
        auditLogger.record(new AuditEntry("r".repeat(100), USER, CUSTOMER, null, "a", false, "FAILED",
                "X".repeat(100), List.of(t), java.util.Map.of(), List.of(), 5, 1, 0, 0, 0, List.of(), null, null, false, null, 0, 0, null));
        Document doc = mongo.getCollection(COLL).find(new Document("traceId", "r".repeat(64))).first();
        assertThat(doc).isNotNull();
        assertThat(doc.getString("question")).isEmpty();
        Document call = doc.getList("toolCalls", Document.class).get(0);
        assertThat(call.getString("toolName")).hasSize(64);
        assertThat(call.getString("errorCode")).hasSize(64);
        assertThat(doc.getString("errorCode")).hasSize(64);
    }

    @Test
    void overlongUserAndRoleAreClipped() {
        UserContext user = new UserContext("u".repeat(100), "Officer", "R".repeat(50), Set.of());
        CustomerContext customer = new CustomerContext(new CertNo(TestCustomers.RETIREE), AccessLevel.LIMITED, Set.of(), Set.of());
        auditLogger.record(new AuditEntry("req-clip-1", user, customer, "q", "a", false, "ANSWERED", null,
                List.of(), java.util.Map.of(), List.of(), 1, 1, 0, 0, 0, List.of(), null, null, false, null, 0, 0, null));
        Document doc = find("req-clip-1");
        assertThat(doc.getString("userId")).hasSize(64);
        assertThat(doc.getString("roleKey")).hasSize(32);
    }

    @Test
    void failedEntryStoresNullAnswerAndErrorCode() {
        auditLogger.record(new AuditEntry("req-fail-1", USER, CUSTOMER, "q", null, false, "FAILED",
                "AI_LLM_TIMEOUT", List.of(), java.util.Map.of(), List.of(), 100, 1, 0, 0, 0, List.of(), null, null, false, null, 0, 0, null));
        Document doc = find("req-fail-1");
        assertThat(doc.get("answer")).isNull();
        assertThat(doc.getBoolean("answerTruncated")).isFalse();
        assertThat(doc.getString("outcome")).isEqualTo("FAILED");
        assertThat(doc.getString("errorCode")).isEqualTo("AI_LLM_TIMEOUT");
    }

    @Test
    void routeFieldsAreWrittenAndExplicitNullWhenUnknown() {
        auditLogger.record(new AuditEntry("req-route-1", USER, CUSTOMER, "q", "a", false, "ANSWERED", null,
                List.of(), java.util.Map.of(), List.of(), 10, 2, 0, 0, 0, List.of(), "CHIP",
                "instruction.contact-prep", false, null, 0, 0, null));
        Document doc = find("req-route-1");
        assertThat(doc.getString("routeSource")).isEqualTo("CHIP");
        assertThat(doc.getString("instructionKey")).isEqualTo("instruction.contact-prep");

        auditLogger.record(entry("req-route-2", "q", "a", List.of()));
        Document unknown = find("req-route-2");
        assertThat(unknown.containsKey("routeSource")).isTrue();
        assertThat(unknown.get("routeSource")).isNull();
        assertThat(unknown.containsKey("instructionKey")).isTrue();
        assertThat(unknown.get("instructionKey")).isNull();
    }

    @Test
    void summaryFieldsAreWritten() {
        auditLogger.record(new AuditEntry("req-sum-1", USER, CUSTOMER, "q", "a", false, "ANSWERED", null,
                List.of(), java.util.Map.of(), List.of(), 10, 2, 0, 0, 0, List.of(), null, null,
                true, "SUMMARIZED", 7, 30, null));
        Document doc = find("req-sum-1");
        assertThat(doc.getBoolean("summaryIn")).isTrue();
        assertThat(doc.getString("summaryStatus")).isEqualTo("SUMMARIZED");
        assertThat(doc.get("summarizedMessages")).isEqualTo(7);
        assertThat(doc.get("summaryTokens")).isEqualTo(30);
    }

    @Test
    void summaryFieldsDefaultWhenNotTriggered() {
        auditLogger.record(entry("req-sum-2", "q", "a", List.of()));
        Document doc = find("req-sum-2");
        assertThat(doc.containsKey("summaryStatus")).isTrue();
        assertThat(doc.get("summaryStatus")).isNull();
        assertThat(doc.get("summarizedMessages")).isEqualTo(0);
        assertThat(doc.get("summaryTokens")).isEqualTo(0);
        assertThat(doc.getBoolean("summaryIn")).isFalse();
    }

    @Test
    void modelNameIsWrittenClippedAndExplicitNullWhenUnknown() {
        auditLogger.record(modelEntry("req-model-1", "gpt-6-luna-2026-08-01"));
        assertThat(find("req-model-1").getString("modelName")).isEqualTo("gpt-6-luna-2026-08-01");

        auditLogger.record(modelEntry("req-model-2", "m".repeat(100)));
        assertThat(find("req-model-2").getString("modelName")).hasSize(64);

        auditLogger.record(modelEntry("req-model-3", null));
        Document unknown = find("req-model-3");
        assertThat(unknown.containsKey("modelName")).isTrue();
        assertThat(unknown.get("modelName")).isNull();
    }

    private AuditEntry modelEntry(String traceId, String modelName) {
        return new AuditEntry(traceId, USER, CUSTOMER, "q", "a", false, "ANSWERED", null, List.of(),
                java.util.Map.of(), List.of(), 10, 1, 0, 0, 0, List.of(), null, null, false, null, 0, 0, modelName);
    }

    private AuditEntry versionedEntry(String traceId, List<PromptVersion> versions) {
        return new AuditEntry(traceId, USER, CUSTOMER, "q", "a", false, "ANSWERED", null, List.of(),
                java.util.Map.of(), List.of(), 10, 2, 6500, 350, 6850, versions, null, null, false, null, 0, 0, null);
    }

    @Test
    void writesTokenAndVersionFields() {
        auditLogger.record(versionedEntry("req-tok-1", List.of(new PromptVersion("system.main", 3),
                new PromptVersion("instruction.sales", 2))));

        Document doc = find("req-tok-1");
        assertThat(doc.get("modelCalls")).isEqualTo(2);
        assertThat(doc.get("inputTokens")).isEqualTo(6500);
        assertThat(doc.get("outputTokens")).isEqualTo(350);
        assertThat(doc.get("totalTokens")).isEqualTo(6850);
        assertThat(doc.getList("promptVersions", Document.class)).containsExactly(
                new Document("configKey", "system.main").append("version", 3),
                new Document("configKey", "instruction.sales").append("version", 2));
        assertThat(doc.containsKey("feedback")).isTrue();
        assertThat(doc.get("feedback")).isNull();
        assertThat(doc.containsKey("feedbackAt")).isTrue();
        assertThat(doc.get("feedbackAt")).isNull();
    }

    @Test
    void promptVersionsCappedAt100() {
        List<PromptVersion> versions = java.util.stream.IntStream.range(0, 120)
                .mapToObj(i -> new PromptVersion("instruction.k" + i, 1)).toList();

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(AuditLogger.class, Level.WARN)) {
            auditLogger.record(versionedEntry("req-vcap-1", versions));
            events = capture.events();
        }

        assertThat(find("req-vcap-1").getList("promptVersions", Document.class)).hasSize(100);
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getMessage().getFormattedMessage()).contains("req-vcap-1", "120");
        });
    }
    @Test
    void writeFailureDoesNotThrow() {
        AuditRepository broken = mock(AuditRepository.class);
        doThrow(new IllegalStateException("db down: secret")).when(broken).insert(any(AuditRecord.class));
        AuditLogger logger = new AuditLogger(broken, Clock.systemUTC(), new CredentialMasker(), propsWith(false), new PiiRedactor());

        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(AuditLogger.class)) {
            assertThatCode(() -> logger.record(entry("req-err-1", "q", "a", List.of()))).doesNotThrowAnyException();
            events = capture.events();
        }

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(events.get(0).getMessage().getFormattedMessage())
                .contains("req-err-1", "java.lang.IllegalStateException")
                .doesNotContain("db down", "secret");
    }
}
