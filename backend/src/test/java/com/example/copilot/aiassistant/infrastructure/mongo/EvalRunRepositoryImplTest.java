package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.domain.eval.CaseResult;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

class EvalRunRepositoryImplTest extends IntegrationTestBase {

    private static final Date NOW = Date.from(Instant.parse("2026-10-20T01:00:00Z"));

    @Autowired MongoTemplate mongo;
    @Autowired EvalRunRepository evalRuns;

    @BeforeEach
    @AfterEach
    void clean() {
        mongo.getCollection(AiAssistantCollections.EVAL_RUN).deleteMany(new Document());
    }

    @Test
    void readsLegacyRunWithoutRouteFields() {
        Document legacyResult = new Document("outcome", "ANSWERED").append("errorCode", null).append("answer", "a")
                .append("toolCalls", List.of(new Document("toolName", "tool").append("status", "EXECUTED")))
                .append("modelCalls", 1).append("inputTokens", 2).append("outputTokens", 3).append("totalTokens", 5)
                .append("durationMs", 9).append("unverifiedNumbers", List.of("1")).append("toolsOk", true)
                .append("numbersOk", true).append("missingPhrases", List.of()).append("forbiddenPhrases", List.of())
                .append("judge", new Document("refused", false)
                        .append("rubric", List.of(new Document("text", "r").append("met", true)))
                        .append("comment", "ok").append("error", null).append("tokens", 7))
                .append("passed", true).append("routeOk", null).append("routeSource", null)
                .append("instructionKey", null);
        Document legacyBaseline = new Document(legacyResult);
        legacyBaseline.remove("routeOk");
        legacyBaseline.remove("routeSource");
        legacyBaseline.remove("instructionKey");
        Document legacyCase = new Document("caseId", "c1").append("group", "g").append("question", "q")
                .append("expectRefusal", false).append("expectCategory", null)
                .append("baseline", legacyBaseline).append("candidate", legacyResult);
        Document legacyMetrics = new Document("correct", 1).append("total", 1).append("toolsOk", 1)
                .append("refusedExpected", 0).append("expectedRefusals", 0).append("refusedNormal", 0)
                .append("normalCases", 1).append("avgTotalTokens", 5L).append("avgInputTokens", 2L)
                .append("avgOutputTokens", 3L).append("avgDurationMs", 9L);
        ObjectId id = new ObjectId();
        mongo.getCollection(AiAssistantCollections.EVAL_RUN).insertOne(new Document("_id", id)
                .append("configKey", "instruction.legacy").append("candidateVersion", 2)
                .append("candidateDocVersion", 3L).append("candidateUpdatedAt", NOW)
                .append("baselineVersions", List.of(new Document("configKey", "instruction.x").append("version", 1)))
                .append("datasetHash", "a".repeat(64)).append("status", "COMPLETED").append("errorCode", null)
                .append("triggeredBy", "tester").append("startedAt", NOW).append("heartbeatAt", NOW)
                .append("finishedAt", NOW).append("totalCases", 1).append("completedCases", 1)
                .append("summary", new Document("baseline", legacyMetrics).append("candidate", legacyMetrics)
                        .append("judgeTokens", 14L))
                .append("cases", List.of(legacyCase)).append("docVersion", 4L));

        EvalRun run = evalRuns.findById(id.toHexString()).orElseThrow();

        assertThat(run.id()).isEqualTo(id.toHexString());
        assertThat(run.status()).isEqualTo(EvalStatus.COMPLETED);
        assertThat(run.candidateVersion()).isEqualTo(2);
        assertThat(run.candidateDocVersion()).isEqualTo(3L);
        assertThat(run.candidateUpdatedAt()).isEqualTo(NOW.toInstant());
        assertThat(run.docVersion()).isEqualTo(4L);
        assertThat(run.baselineVersions()).singleElement().satisfies(v -> {
            assertThat(v.configKey()).isEqualTo("instruction.x");
            assertThat(v.version()).isEqualTo(1);
        });
        assertThat(run.summary().baseline().routeOk()).isZero();
        assertThat(run.summary().baseline().routeCases()).isZero();
        assertThat(run.summary().baseline().avgTotalTokens()).isEqualTo(5L);
        assertThat(run.summary().judgeTokens()).isEqualTo(14L);
        CaseResult baseline = run.cases().get(0).baseline();
        assertThat(baseline.routeOk()).isNull();
        assertThat(baseline.routeSource()).isNull();
        assertThat(baseline.instructionKey()).isNull();
        assertThat(baseline.judge().tokens()).isEqualTo(7);
        assertThat(baseline.judge().rubric()).singleElement().satisfies(x -> assertThat(x.met()).isTrue());
        assertThat(baseline.toolCalls()).singleElement().satisfies(t -> assertThat(t.toolName()).isEqualTo("tool"));
        assertThat(baseline.unverifiedNumbers()).containsExactly("1");
        assertThat(baseline.passed()).isTrue();
    }
}
