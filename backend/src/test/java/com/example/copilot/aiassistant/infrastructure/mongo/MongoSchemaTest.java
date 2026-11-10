package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.infrastructure.config.EvalConfiguration;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.mongodb.MongoWriteException;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;

class MongoSchemaTest extends IntegrationTestBase {

    private static final String CONFIG = "COLL_ICRM_AI_ASSISTANT_CONFIG";
    private static final String AUDIT = "COLL_ICRM_AI_ASSISTANT_AUDIT";

    @Autowired MongoTemplate mongo;
    @Autowired AssistantConfigMongoRepository repository;
    @Autowired SeedLoader seedLoader;
    @Autowired MongoSchemaInitializer initializer;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @AfterEach
    void restoreSeed() {
        repository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    private Document rawDoc(String status, Object scheduledAt) {
        return new Document("configKey", "instruction.raw")
                .append("configType", "SYSTEM_INSTRUCTION")
                .append("name", "raw")
                .append("content", "c")
                .append("status", status)
                .append("version", 1)
                .append("createdBy", "t")
                .append("createdAt", new Date())
                .append("updatedBy", "t")
                .append("updatedAt", new Date())
                .append("scheduledAt", scheduledAt)
                .append("docVersion", 0L);
    }

    private AssistantConfigDocument config(String key, int version, ConfigStatus status) {
        AssistantConfigDocument c = new AssistantConfigDocument();
        c.setConfigKey(key);
        c.setConfigType(ConfigType.SYSTEM_INSTRUCTION);
        c.setName("n");
        c.setContent("c");
        c.setStatus(status);
        c.setVersion(version);
        c.setCreatedBy("t");
        c.setCreatedAt(Instant.now());
        c.setUpdatedBy("t");
        c.setUpdatedAt(Instant.now());
        return c;
    }

    @Test
    void validatorRejectsUnknownStatus() {
        assertThatThrownBy(() -> mongo.getCollection(CONFIG).insertOne(rawDoc("LIVE", null)))
                .isInstanceOf(MongoWriteException.class);
    }

    @Test
    void validatorAcceptsDiscarded() {
        mongo.getCollection(CONFIG).insertOne(rawDoc("DISCARDED", new Date()));
        assertThat(repository.findByConfigKeyAndVersion("instruction.raw", 1).orElseThrow().getStatus())
                .isEqualTo(ConfigStatus.DISCARDED);
    }

    @Test
    void validatorRequiresScheduledAtForScheduled() {
        assertThatThrownBy(() -> mongo.getCollection(CONFIG).insertOne(rawDoc("SCHEDULED", null)))
                .isInstanceOf(MongoWriteException.class);
        mongo.getCollection(CONFIG).insertOne(rawDoc("SCHEDULED", new Date()));
    }

    @Test
    void validatorRejectsSystemPromptOtherThanSystemMain() {
        Document other = rawDoc("DRAFT", null).append("configType", "SYSTEM_PROMPT")
                .append("configKey", "system.other");
        assertThatThrownBy(() -> mongo.getCollection(CONFIG).insertOne(other))
                .isInstanceOf(MongoWriteException.class);
        mongo.getCollection(CONFIG).insertOne(rawDoc("DRAFT", null).append("configType", "SYSTEM_PROMPT")
                .append("configKey", "system.main"));
    }

    @Test
    void validatorRejectsInstructionWithoutInstructionPrefix() {
        Document bad = rawDoc("DRAFT", null).append("configKey", "system.raw");
        assertThatThrownBy(() -> mongo.getCollection(CONFIG).insertOne(bad))
                .isInstanceOf(MongoWriteException.class);
    }

    @Test
    void uniqueVersionPerKey() {
        repository.save(config("instruction.dup", 1, ConfigStatus.ARCHIVED));
        assertThatThrownBy(() -> repository.save(config("instruction.dup", 1, ConfigStatus.ARCHIVED)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void onlyOnePublishedPerKey() {
        repository.save(config("instruction.dup", 1, ConfigStatus.PUBLISHED));
        assertThatThrownBy(() -> repository.save(config("instruction.dup", 2, ConfigStatus.PUBLISHED)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private Document auditDoc(String traceId, String resultHash) {
        Document call = new Document("toolName", "getStockQuote").append("status", "SUCCESS")
                .append("errorCode", null).append("durationMs", 5).append("citationId", 1)
                .append("target", "台積電 2330").append("dataTime", "2026-10-16").append("resultCount", 1)
                .append("resultHash", resultHash).append("result", new Document("close", 2420));
        return new Document("traceId", traceId).append("userId", "u").append("roleKey", "r")
                .append("accessLevel", "FULL").append("question", "q")
                .append("answer", "a [1]").append("answerTruncated", false).append("outcome", "ANSWERED")
                .append("errorCode", null).append("toolCalls", java.util.List.of(call))
                .append("unverifiedNumbers", java.util.List.of("3000")).append("durationMs", 5)
                .append("createdAt", new Date());
    }

    @Test
    void auditValidatorAcceptsCitationFields() {
        mongo.getCollection(AUDIT).insertOne(auditDoc("schema-ok-1", "a".repeat(64)));
        assertThat(mongo.getCollection(AUDIT).countDocuments(new Document("traceId", "schema-ok-1")))
                .isEqualTo(1);
    }

    @Test
    void auditWithoutTraceIdIsNotAccepted() {
        Document doc = auditDoc("schema-no-trace", "a".repeat(64));
        doc.remove("traceId");
        doc.append("requestId", "schema-no-trace");
        assertThatThrownBy(() -> mongo.getCollection(AUDIT).insertOne(doc)).isInstanceOf(MongoWriteException.class);
    }

    @Test
    void auditValidatorRejectsLongResultHash() {
        assertThatThrownBy(() -> mongo.getCollection(AUDIT).insertOne(auditDoc("schema-bad-1", "a".repeat(65))))
                .isInstanceOf(MongoWriteException.class);
    }


    private Document auditDocWithFeedback(String traceId, String feedback) {
        return auditDoc(traceId, "a".repeat(64)).append("modelCalls", 2).append("inputTokens", 6500)
                .append("outputTokens", 350).append("totalTokens", 6850)
                .append("promptVersions", java.util.List.of(
                        new Document("configKey", "system.main").append("version", 1)))
                .append("feedback", feedback).append("feedbackAt", feedback == null ? null : new Date());
    }

    @Test
    void auditValidatorAcceptsNewFields() {
        mongo.getCollection(AUDIT).insertOne(auditDocWithFeedback("schema-ok-2", null));
        mongo.getCollection(AUDIT).insertOne(auditDocWithFeedback("schema-ok-3", "UP"));
        assertThat(mongo.getCollection(AUDIT).countDocuments(new Document("traceId",
                new Document("$in", java.util.List.of("schema-ok-2", "schema-ok-3"))))).isEqualTo(2);
    }

    @Test
    void auditValidatorChecksRouteSource() {
        mongo.getCollection(AUDIT).insertOne(auditDocWithFeedback("schema-route-ok", null)
                .append("routeSource", "CHIP").append("instructionKey", "instruction.contact-prep"));
        mongo.getCollection(AUDIT).insertOne(auditDocWithFeedback("schema-route-null", null)
                .append("routeSource", null).append("instructionKey", null));
        assertThatThrownBy(() -> mongo.getCollection(AUDIT).insertOne(
                auditDocWithFeedback("schema-route-bad", null).append("routeSource", "OTHER")))
                .isInstanceOf(MongoWriteException.class);
    }

    @Test
    void auditValidatorChecksSummaryFields() {
        mongo.getCollection(AUDIT).insertOne(auditDocWithFeedback("schema-sum-ok1", null)
                .append("summaryIn", true).append("summaryStatus", "SUMMARIZED")
                .append("summarizedMessages", 7).append("summaryTokens", 30));
        mongo.getCollection(AUDIT).insertOne(auditDocWithFeedback("schema-sum-ok2", null)
                .append("summaryIn", true).append("summaryStatus", "FAILED")
                .append("summarizedMessages", 2).append("summaryTokens", 0));
        mongo.getCollection(AUDIT).insertOne(auditDocWithFeedback("schema-sum-ok3", null)
                .append("summaryIn", false).append("summaryStatus", null)
                .append("summarizedMessages", 0).append("summaryTokens", 0));
        assertThatThrownBy(() -> mongo.getCollection(AUDIT).insertOne(
                auditDocWithFeedback("schema-sum-bad1", null).append("summaryStatus", "OTHER")))
                .isInstanceOf(MongoWriteException.class);
        assertThatThrownBy(() -> mongo.getCollection(AUDIT).insertOne(
                auditDocWithFeedback("schema-sum-bad2", null).append("summarizedMessages", -1)))
                .isInstanceOf(MongoWriteException.class);
    }

    @Test
    void auditValidatorChecksModelName() {
        mongo.getCollection(AUDIT).insertOne(auditDocWithFeedback("schema-model-ok", null)
                .append("modelName", "m".repeat(64)));
        mongo.getCollection(AUDIT).insertOne(auditDocWithFeedback("schema-model-null", null)
                .append("modelName", null));
        assertThatThrownBy(() -> mongo.getCollection(AUDIT).insertOne(
                auditDocWithFeedback("schema-model-long", null).append("modelName", "m".repeat(65))))
                .isInstanceOf(MongoWriteException.class);
        assertThatThrownBy(() -> mongo.getCollection(AUDIT).insertOne(
                auditDocWithFeedback("schema-model-type", null).append("modelName", 1)))
                .isInstanceOf(MongoWriteException.class);
    }

    @Test
    void auditValidatorRejectsBadFeedback() {
        assertThatThrownBy(() -> mongo.getCollection(AUDIT).insertOne(auditDocWithFeedback("schema-bad-2", "MAYBE")))
                .isInstanceOf(MongoWriteException.class);
    }
    @Test
    void auditIndexes() {
        java.util.List<IndexInfo> audit = mongo.indexOps(AUDIT).getIndexInfo();
        assertThat(audit).extracting(IndexInfo::getName).doesNotContain("IX_ICRM_AI_AUDIT_3");
        IndexInfo byTrace = audit.stream().filter(i -> i.getName().equals("IX_ICRM_AI_AUDIT_4"))
                .findFirst().orElseThrow();
        assertThat(byTrace.getIndexFields().stream().map(f -> f.getKey()).toList()).containsExactly("traceId");
        IndexInfo byVersion = audit.stream().filter(i -> i.getName().equals("IX_ICRM_AI_AUDIT_5"))
                .findFirst().orElseThrow();
        assertThat(byVersion.getIndexFields().stream().map(f -> f.getKey()).toList())
                .containsExactly("promptVersions.configKey", "promptVersions.version");
        assertThat(byVersion.getPartialFilterExpression()).isNotBlank();
    }


    private static final String EVAL_RUN = "COLL_ICRM_AI_ASSISTANT_EVAL_RUN";

    private Document evalSide(int toolCalls) {
        java.util.List<Document> calls = new java.util.ArrayList<>();
        for (int i = 0; i < toolCalls; i++) {
            calls.add(new Document("toolName", "getStockQuote").append("status", "SUCCESS"));
        }
        return new Document("outcome", "ANSWERED").append("errorCode", null).append("answer", "a")
                .append("toolCalls", calls).append("modelCalls", 2).append("inputTokens", 100)
                .append("outputTokens", 50).append("totalTokens", 150).append("durationMs", 900)
                .append("unverifiedNumbers", java.util.List.of()).append("toolsOk", true).append("numbersOk", true)
                .append("missingPhrases", java.util.List.of()).append("forbiddenPhrases", java.util.List.of())
                .append("judge", new Document("refused", false)
                        .append("rubric", java.util.List.of(new Document("text", "r").append("met", true)))
                        .append("comment", "ok").append("error", null).append("tokens", 40))
                .append("passed", true);
    }

    private Document evalCase(String id, Document side) {
        return new Document("caseId", id).append("group", "g").append("question", "q")
                .append("expectRefusal", false).append("baseline", side).append("candidate", side);
    }

    private Document evalMetrics(Object correct) {
        return new Document("correct", correct).append("total", 1).append("toolsOk", 1)
                .append("refusedExpected", 0).append("expectedRefusals", 0).append("refusedNormal", 0)
                .append("normalCases", 1).append("avgTotalTokens", 150L).append("avgInputTokens", 100L)
                .append("avgOutputTokens", 50L).append("avgDurationMs", 900L);
    }

    private void assertEvalRejected(Document doc) {
        assertThatThrownBy(() -> mongo.getCollection(EVAL_RUN).insertOne(doc))
                .isInstanceOf(MongoWriteException.class);
    }

    @Test
    void evalRunValidatorChecksCaseResultAndSummary() {
        mongo.getCollection(EVAL_RUN).deleteMany(new Document());
        Document longAnswer = evalSide(1).append("answer", "x".repeat(8001));
        assertEvalRejected(evalRunDoc("COMPLETED", 0).append("cases",
                java.util.List.of(evalCase("A1", evalSide(1)).append("candidate", longAnswer))));
        assertEvalRejected(evalRunDoc("COMPLETED", 0).append("cases",
                java.util.List.of(evalCase("A1", evalSide(11)))));
        assertEvalRejected(evalRunDoc("COMPLETED", 0).append("summary", new Document("baseline", evalMetrics("1"))
                .append("candidate", evalMetrics(1)).append("judgeTokens", 5L)));
        mongo.getCollection(EVAL_RUN).insertOne(evalRunDoc("COMPLETED", 0).append("summary",
                new Document("baseline", evalMetrics(1)).append("candidate", evalMetrics(1))
                        .append("judgeTokens", 5L)));
        mongo.getCollection(EVAL_RUN).deleteMany(new Document());
    }

    private Document evalRunDoc(String status, int cases) {
        java.util.List<Document> list = new java.util.ArrayList<>();
        for (int i = 0; i < cases; i++) {
            list.add(evalCase("A" + i, evalSide(10)));
        }
        return new Document("configKey", "instruction.raw").append("candidateVersion", 2)
                .append("candidateDocVersion", 0L).append("candidateUpdatedAt", new Date())
                .append("baselineVersions", java.util.List.of(
                        new Document("configKey", "system.main").append("version", 1)))
                .append("datasetHash", "a".repeat(64)).append("status", status).append("errorCode", null)
                .append("triggeredBy", "t").append("startedAt", new Date()).append("heartbeatAt", new Date())
                .append("finishedAt", null).append("totalCases", 16).append("completedCases", 0)
                .append("summary", null).append("cases", list).append("docVersion", 0L);
    }

    @Test
    void evalRunValidatorAcceptsRouteFields() {
        mongo.getCollection(EVAL_RUN).deleteMany(new Document());
        Document side = evalSide(1).append("routeOk", true).append("routeSource", "ROUTER")
                .append("instructionKey", "instruction.contact-prep");
        Document nullSide = evalSide(1).append("routeOk", null).append("routeSource", null)
                .append("instructionKey", null);
        Document metrics = evalMetrics(1).append("routeOk", 1).append("routeCases", 1);
        mongo.getCollection(EVAL_RUN).insertOne(evalRunDoc("COMPLETED", 0)
                .append("cases", java.util.List.of(
                        evalCase("A1", side).append("expectCategory", "instruction.contact-prep"),
                        evalCase("A2", nullSide).append("expectCategory", null)))
                .append("summary", new Document("baseline", metrics).append("candidate", metrics)
                        .append("judgeTokens", 5L)));
        mongo.getCollection(EVAL_RUN).deleteMany(new Document());
        assertEvalRejected(evalRunDoc("COMPLETED", 0).append("cases", java.util.List.of(
                evalCase("A1", evalSide(1).append("routeSource", "OTHER")))));
        assertEvalRejected(evalRunDoc("COMPLETED", 0).append("cases", java.util.List.of(
                evalCase("A1", evalSide(1).append("routeOk", "yes")))));
        assertEvalRejected(evalRunDoc("COMPLETED", 0).append("cases", java.util.List.of(
                evalCase("A1", evalSide(1).append("instructionKey", "k".repeat(65))))));
        assertEvalRejected(evalRunDoc("COMPLETED", 0).append("cases", java.util.List.of(
                evalCase("A1", evalSide(1)).append("expectCategory", "k".repeat(65)))));
        assertEvalRejected(evalRunDoc("COMPLETED", 0).append("summary", new Document("baseline",
                evalMetrics(1).append("routeOk", -1)).append("candidate", evalMetrics(1)).append("judgeTokens", 5L)));
        assertEvalRejected(evalRunDoc("COMPLETED", 0).append("summary", new Document("baseline",
                evalMetrics(1).append("routeCases", "1")).append("candidate", evalMetrics(1))
                .append("judgeTokens", 5L)));
    }

    @Test
    void evalRunValidatorRejectsBadStatusAndTooManyCases() {
        mongo.getCollection(EVAL_RUN).deleteMany(new Document());
        assertThatThrownBy(() -> mongo.getCollection(EVAL_RUN).insertOne(evalRunDoc("DONE", 0)))
                .isInstanceOf(MongoWriteException.class);
        int max = EvalConfiguration.STORED_CASES_MAX;
        assertThatThrownBy(() -> mongo.getCollection(EVAL_RUN).insertOne(evalRunDoc("COMPLETED", max + 1)))
                .isInstanceOf(MongoWriteException.class);
        mongo.getCollection(EVAL_RUN).insertOne(evalRunDoc("COMPLETED", max));
        mongo.getCollection(EVAL_RUN).deleteMany(new Document());
    }

    @Test
    void evalRunIndexes() {
        java.util.List<IndexInfo> indexes = mongo.indexOps(EVAL_RUN).getIndexInfo();
        IndexInfo running = indexes.stream().filter(i -> i.getName().equals("UK_ICRM_AI_EVAL_1"))
                .findFirst().orElseThrow();
        assertThat(running.isUnique()).isTrue();
        assertThat(running.getPartialFilterExpression()).contains("RUNNING");
        IndexInfo history = indexes.stream().filter(i -> i.getName().equals("IX_ICRM_AI_EVAL_1"))
                .findFirst().orElseThrow();
        assertThat(history.getIndexFields().stream().map(f -> f.getKey()).toList())
                .containsExactly("configKey", "startedAt");
        IndexInfo ttl = indexes.stream().filter(i -> i.getName().equals("IX_ICRM_AI_EVAL_TTL"))
                .findFirst().orElseThrow();
        assertThat(ttl.getExpireAfter().orElseThrow().toSeconds()).isEqualTo(15_552_000L);
    }
    @Test
    void indexNamesMatchSpec() {
        Set<String> config = mongo.indexOps(CONFIG).getIndexInfo().stream()
                .map(IndexInfo::getName).collect(Collectors.toSet());
        Set<String> audit = mongo.indexOps(AUDIT).getIndexInfo().stream()
                .map(IndexInfo::getName).collect(Collectors.toSet());
        assertThat(config).contains("UK_ICRM_AI_CFG_1", "UK_ICRM_AI_CFG_2", "UK_ICRM_AI_CFG_3",
                "IX_ICRM_AI_CFG_4", "IX_ICRM_AI_CFG_5");
        assertThat(audit).contains("IX_ICRM_AI_AUDIT_TTL", "IX_ICRM_AI_AUDIT_2");
        IndexInfo ttl = mongo.indexOps(AUDIT).getIndexInfo().stream()
                .filter(i -> i.getName().equals("IX_ICRM_AI_AUDIT_TTL")).findFirst().orElseThrow();
        assertThat(ttl.getExpireAfter().orElseThrow().toSeconds()).isEqualTo(157_680_000L);
    }

    @Test
    void startupReplacesLegacyTtlIndexes() {
        replaceWithLegacy(AUDIT, "IX_ICRM_AI_AUDIT_TTL", "IX_ICRM_AI_AUDIT_1", "createdAt");
        replaceWithLegacy(EVAL_RUN, "IX_ICRM_AI_EVAL_TTL", "IX_ICRM_AI_EVAL_2", "startedAt");

        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        assertThat(ttlSeconds(AUDIT, "IX_ICRM_AI_AUDIT_TTL")).isEqualTo(157_680_000L);
        assertThat(ttlSeconds(EVAL_RUN, "IX_ICRM_AI_EVAL_TTL")).isEqualTo(15_552_000L);
        assertThat(indexNames(AUDIT)).doesNotContain("IX_ICRM_AI_AUDIT_1");
        assertThat(indexNames(EVAL_RUN)).doesNotContain("IX_ICRM_AI_EVAL_2");
    }

    /** Puts a collection in the state a database created before the rename is in: old name, 180-day TTL. */
    private void replaceWithLegacy(String collection, String current, String legacy, String field) {
        mongo.indexOps(collection).dropIndex(current);
        mongo.indexOps(collection).createIndex(new Index().named(legacy).on(field, Sort.Direction.ASC)
                .expire(15_552_000L));
    }

    private Set<String> indexNames(String collection) {
        return mongo.indexOps(collection).getIndexInfo().stream().map(IndexInfo::getName)
                .collect(Collectors.toSet());
    }

    private long ttlSeconds(String collection, String name) {
        return mongo.indexOps(collection).getIndexInfo().stream().filter(i -> i.getName().equals(name))
                .findFirst().orElseThrow().getExpireAfter().orElseThrow().toSeconds();
    }
}
