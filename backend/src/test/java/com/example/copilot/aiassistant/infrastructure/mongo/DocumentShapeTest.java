package com.example.copilot.aiassistant.infrastructure.mongo;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import com.example.copilot.aiassistant.domain.chat.EvaluationReport;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import com.example.copilot.aiassistant.domain.eval.CaseResult;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalMetrics;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import com.example.copilot.aiassistant.domain.eval.EvalSummary;
import com.example.copilot.aiassistant.domain.eval.JudgeVerdict;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.domain.repository.AuditRepository;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Pins the stored shape (top-level and nested key sets) of the three collections as raw documents, so a refactor of
 * the persistence layer cannot silently add, rename or drop a field (or write {@code _class}).
 */
class DocumentShapeTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-20T01:00:00Z");

    @Autowired MongoTemplate mongo;
    @Autowired AssistantConfigRepository configs;
    @Autowired AuditRepository audits;
    @Autowired EvalRunRepository evalRuns;

    @BeforeEach
    @AfterEach
    void clean() {
        mongo.getCollection(AiAssistantCollections.CONFIG).deleteMany(new Document());
        mongo.getCollection(AiAssistantCollections.AUDIT).deleteMany(new Document());
        mongo.getCollection(AiAssistantCollections.EVAL_RUN).deleteMany(new Document());
    }

    @Test
    void configDocumentShape() {
        configs.insert(new AssistantConfig(null, new ConfigKey("instruction.shape"), ConfigType.SYSTEM_INSTRUCTION, "n",
                "c", ConfigStatus.SCHEDULED, 2, 1, "note", "creator", NOW, "editor", NOW, NOW.plusSeconds(60), null));

        Document doc = mongo.getCollection(AiAssistantCollections.CONFIG).find(eq("configKey", "instruction.shape"))
                .first();

        assertThat(doc).isNotNull();
        assertThat(doc.keySet()).isEqualTo(Set.of("_id", "configKey", "configType", "name", "content", "status",
                "version", "basedOnVersion", "changeNote", "createdBy", "createdAt", "updatedBy", "updatedAt",
                "scheduledAt", "docVersion"));
        assertThat(doc).doesNotContainKey("_class");
    }

    @Test
    void auditDocumentShape() {
        String traceId = "0123456789abcdef0123456789abcdef";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", List.of(Map.of("a", 1), Map.of("b", List.of("x"))));
        result.put("meta", Map.of("total", 2));
        audits.insert(new AuditRecord(null, traceId, "u1", "role", "FULL", "q?", null, false, "FAILED", null,
                List.of(new AuditRecord.ToolCall("tool", "SUCCESS", null, 5, 1, "t", "d", 2, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", result)), 10,
                NOW, List.of("1"), 1, 2, 3, 5, List.of(new PromptVersion("instruction.x", 4)), "ROUTER", null,
                false, null, 0, 0, null));

        Document doc = mongo.getCollection(AiAssistantCollections.AUDIT).find(eq("traceId", traceId)).first();

        assertThat(doc).isNotNull();
        assertThat(doc.keySet()).isEqualTo(Set.of("_id", "traceId", "userId", "roleKey", "accessLevel", "question",
                "answer", "answerTruncated", "outcome", "errorCode", "toolCalls", "durationMs", "createdAt",
                "unverifiedNumbers", "modelCalls", "inputTokens", "outputTokens", "totalTokens", "promptVersions",
                "routeSource", "instructionKey", "summaryIn", "summaryStatus", "summarizedMessages", "summaryTokens",
                "modelName", "feedback", "feedbackAt"));
        for (String nullField : List.of("answer", "errorCode", "instructionKey", "modelName", "feedback", "feedbackAt")) {
            assertThat(doc).containsKey(nullField);
            assertThat(doc.get(nullField)).isNull();
        }
        Document toolCall = doc.getList("toolCalls", Document.class).get(0);
        assertThat(toolCall.keySet()).isEqualTo(Set.of("toolName", "status", "errorCode", "durationMs", "citationId",
                "target", "dataTime", "resultCount", "resultHash", "result"));
        assertThat(toolCall.get("errorCode")).isNull();
        Document nested = toolCall.get("result", Document.class);
        assertThat(nested.keySet()).isEqualTo(Set.of("rows", "meta"));
        assertThat(nested.getList("rows", Document.class)).hasSize(2);
        assertThat(nested.get("meta", Document.class).keySet()).isEqualTo(Set.of("total"));
        assertThat(doc.getList("promptVersions", Document.class).get(0).keySet())
                .isEqualTo(Set.of("configKey", "version"));
        assertThat(doc).doesNotContainKey("_class");
    }

    @Test
    void evalRunDocumentShape() {
        EvalRun run = new EvalRun(null, new ConfigKey("instruction.shape"), 2, 3L, NOW,
                List.of(new PromptVersion("instruction.x", 1)), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", EvalStatus.RUNNING, null, "tester", NOW, NOW,
                null, 1, 0, null, List.of(), 0L);
        String id = evalRuns.insert(run);
        JudgeVerdict judge = new JudgeVerdict(false, List.of(new JudgeVerdict.RubricResult("r", true)), "ok", null, 7);
        CaseResult result = new CaseResult("ANSWERED", null, "a",
                List.of(new EvaluationReport.ToolCall("tool", "EXECUTED")), 1, 2, 3, 5, 9, List.of("1"), true, true,
                List.of(), List.of(), judge, true, true, "ROUTER", "instruction.x");
        EvalCaseRecord c = new EvalCaseRecord("c1", "g", "q", false, "cat", result, result);
        assertThat(evalRuns.appendCase(id, 0L, c, NOW)).isTrue();
        EvalMetrics m = new EvalMetrics(1, 1, 1, 0, 0, 0, 1, 1, 1, 5, 2, 3, 9);
        assertThat(evalRuns.complete(id, 1L, new EvalSummary(m, m, 14L), NOW)).isTrue();

        Document doc = mongo.getCollection(AiAssistantCollections.EVAL_RUN).find(eq("_id", new ObjectId(id))).first();

        assertThat(doc).isNotNull();
        assertThat(doc.keySet()).isEqualTo(Set.of("_id", "configKey", "candidateVersion", "candidateDocVersion",
                "candidateUpdatedAt", "baselineVersions", "datasetHash", "status", "errorCode", "triggeredBy",
                "startedAt", "heartbeatAt", "finishedAt", "totalCases", "completedCases", "summary", "cases",
                "docVersion"));
        assertThat(doc).containsKey("errorCode");
        assertThat(doc.get("errorCode")).isNull();
        assertThat(doc.getList("baselineVersions", Document.class).get(0).keySet())
                .isEqualTo(Set.of("configKey", "version"));
        Document summary = doc.get("summary", Document.class);
        assertThat(summary.keySet()).isEqualTo(Set.of("baseline", "candidate", "judgeTokens"));
        Set<String> metricKeys = Set.of("correct", "total", "toolsOk", "refusedExpected", "expectedRefusals",
                "refusedNormal", "normalCases", "routeOk", "routeCases", "avgTotalTokens", "avgInputTokens",
                "avgOutputTokens", "avgDurationMs");
        assertThat(summary.get("baseline", Document.class).keySet()).isEqualTo(metricKeys);
        assertThat(summary.get("candidate", Document.class).keySet()).isEqualTo(metricKeys);
        Document stored = doc.getList("cases", Document.class).get(0);
        assertThat(stored.keySet()).isEqualTo(Set.of("caseId", "group", "question", "expectRefusal", "expectCategory",
                "baseline", "candidate"));
        Set<String> resultKeys = Set.of("outcome", "errorCode", "answer", "toolCalls", "modelCalls", "inputTokens",
                "outputTokens", "totalTokens", "durationMs", "unverifiedNumbers", "toolsOk", "numbersOk",
                "missingPhrases", "forbiddenPhrases", "judge", "passed", "routeOk", "routeSource", "instructionKey");
        for (String side : List.of("baseline", "candidate")) {
            Document r = stored.get(side, Document.class);
            assertThat(r.keySet()).isEqualTo(resultKeys);
            assertThat(r.getList("toolCalls", Document.class).get(0).keySet()).isEqualTo(Set.of("toolName", "status"));
            Document j = r.get("judge", Document.class);
            assertThat(j.keySet()).isEqualTo(Set.of("refused", "rubric", "comment", "error", "tokens"));
            assertThat(j.getList("rubric", Document.class).get(0).keySet()).isEqualTo(Set.of("text", "met"));
        }
        assertThat(doc).doesNotContainKey("_class");
    }
}
