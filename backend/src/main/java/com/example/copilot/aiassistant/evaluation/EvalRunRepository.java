package com.example.copilot.aiassistant.evaluation;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.aiassistant.chat.EvaluationReport;
import com.example.copilot.aiassistant.prompt.PromptVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Stores evaluation runs. Writes use explicit {@link Document}s (no {@code _class}, nulls kept) and reads go
 * through the explicit mapping below, so both directions stay symmetric. Every update is conditional on
 * {@code docVersion}; {@code false} means the run was changed (e.g. marked interrupted) in the meantime.
 */
@Repository
public class EvalRunRepository {

    private static final String RUNNING = EvalStatus.RUNNING.name();

    private final MongoTemplate mongo;

    public EvalRunRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Inserts the run and returns its id; a second RUNNING run raises DuplicateKeyException. */
    public String insert(EvalRun run) {
        Document doc = toDocument(run);
        mongo.insert(doc, EvalRun.COLLECTION);
        return doc.getObjectId("_id").toHexString();
    }

    public Optional<EvalRun> findById(String id) {
        if (!ObjectId.isValid(id)) {
            return Optional.empty();
        }
        Document doc = mongo.getCollection(EvalRun.COLLECTION).find(new Document("_id", new ObjectId(id))).first();
        return Optional.ofNullable(doc).map(EvalRunRepository::fromDocument);
    }

    public Optional<EvalRun> findRunning() {
        Document doc = mongo.getCollection(EvalRun.COLLECTION).find(new Document("status", RUNNING)).first();
        return Optional.ofNullable(doc).map(EvalRunRepository::fromDocument);
    }

    /** Newest first, without the {@code cases} array. */
    public List<EvalRun> recent(String configKey, int limit) {
        Query query = Query.query(where("configKey").is(configKey)).with(Sort.by(Sort.Direction.DESC, "startedAt"))
                .limit(limit);
        query.fields().exclude("cases");
        return mongo.find(query, Document.class, EvalRun.COLLECTION).stream()
                .map(EvalRunRepository::fromDocument).toList();
    }

    /** Pushes one case result, counts it and refreshes the heartbeat. */
    public boolean appendCase(String id, long docVersion, EvalCaseRecord c, Instant now) {
        Update update = new Update().push("cases", caseDocument(c)).inc("completedCases", 1)
                .set("heartbeatAt", Date.from(now));
        return update(id, docVersion, update);
    }

    /** Heartbeat only: tells the stale check the run is alive and still allowed to continue. */
    public boolean touch(String id, long docVersion, Instant now) {
        return update(id, docVersion, new Update().set("heartbeatAt", Date.from(now)));
    }

    public boolean complete(String id, long docVersion, EvalSummary s, Instant now) {
        Update update = new Update().set("status", EvalStatus.COMPLETED.name()).set("summary", summaryDocument(s))
                .set("finishedAt", Date.from(now));
        return update(id, docVersion, update);
    }

    public boolean fail(String id, long docVersion, String errorCode, Instant now) {
        Update update = new Update().set("status", EvalStatus.FAILED.name()).set("errorCode", errorCode)
                .set("finishedAt", Date.from(now));
        return update(id, docVersion, update);
    }

    private boolean update(String id, long docVersion, Update update) {
        if (!ObjectId.isValid(id)) {
            return false;
        }
        Query query = Query.query(where("_id").is(new ObjectId(id)).and("docVersion").is(docVersion)
                .and("status").is(RUNNING));
        update.inc("docVersion", 1L);
        return mongo.updateFirst(query, update, EvalRun.COLLECTION).getModifiedCount() == 1;
    }

    // ---- Document mapping: run -> document ----

    static Document toDocument(EvalRun r) {
        return new Document()
                .append("configKey", r.configKey())
                .append("candidateVersion", r.candidateVersion())
                .append("candidateDocVersion", r.candidateDocVersion())
                .append("candidateUpdatedAt", Date.from(r.candidateUpdatedAt()))
                .append("baselineVersions", r.baselineVersions().stream().map(v -> new Document()
                        .append("configKey", v.configKey()).append("version", v.version())).toList())
                .append("datasetHash", r.datasetHash())
                .append("status", r.status().name())
                .append("errorCode", r.errorCode())
                .append("triggeredBy", r.triggeredBy())
                .append("startedAt", Date.from(r.startedAt()))
                .append("heartbeatAt", Date.from(r.heartbeatAt()))
                .append("finishedAt", r.finishedAt() == null ? null : Date.from(r.finishedAt()))
                .append("totalCases", r.totalCases())
                .append("completedCases", r.completedCases())
                .append("summary", r.summary() == null ? null : summaryDocument(r.summary()))
                .append("cases", r.cases().stream().map(EvalRunRepository::caseDocument).toList())
                .append("docVersion", r.docVersion());
    }

    private static Document summaryDocument(EvalSummary s) {
        return new Document().append("baseline", metricsDocument(s.baseline()))
                .append("candidate", metricsDocument(s.candidate())).append("judgeTokens", s.judgeTokens());
    }

    private static Document metricsDocument(EvalMetrics m) {
        return new Document().append("correct", m.correct()).append("total", m.total())
                .append("toolsOk", m.toolsOk()).append("refusedExpected", m.refusedExpected())
                .append("expectedRefusals", m.expectedRefusals()).append("refusedNormal", m.refusedNormal())
                .append("normalCases", m.normalCases()).append("routeOk", m.routeOk())
                .append("routeCases", m.routeCases()).append("avgTotalTokens", m.avgTotalTokens())
                .append("avgInputTokens", m.avgInputTokens()).append("avgOutputTokens", m.avgOutputTokens())
                .append("avgDurationMs", m.avgDurationMs());
    }

    private static Document caseDocument(EvalCaseRecord c) {
        return new Document().append("caseId", c.caseId()).append("group", c.group())
                .append("question", c.question()).append("expectRefusal", c.expectRefusal()).append("expectCategory", c.expectCategory())
                .append("baseline", resultDocument(c.baseline())).append("candidate", resultDocument(c.candidate()));
    }

    private static Document resultDocument(CaseResult r) {
        return new Document().append("outcome", r.outcome()).append("errorCode", r.errorCode())
                .append("answer", r.answer())
                .append("toolCalls", r.toolCalls().stream().map(t -> new Document()
                        .append("toolName", t.toolName()).append("status", t.status())).toList())
                .append("modelCalls", r.modelCalls()).append("inputTokens", r.inputTokens())
                .append("outputTokens", r.outputTokens()).append("totalTokens", r.totalTokens())
                .append("durationMs", r.durationMs()).append("unverifiedNumbers", r.unverifiedNumbers())
                .append("toolsOk", r.toolsOk()).append("numbersOk", r.numbersOk())
                .append("missingPhrases", r.missingPhrases()).append("forbiddenPhrases", r.forbiddenPhrases())
                .append("judge", r.judge() == null ? null : judgeDocument(r.judge()))
                .append("passed", r.passed()).append("routeOk", r.routeOk())
                .append("routeSource", r.routeSource()).append("instructionKey", r.instructionKey());
    }

    private static Document judgeDocument(JudgeVerdict j) {
        return new Document().append("refused", j.refused())
                .append("rubric", j.rubric().stream().map(x -> new Document()
                        .append("text", x.text()).append("met", x.met())).toList())
                .append("comment", j.comment()).append("error", j.error()).append("tokens", j.tokens());
    }

    // ---- Document mapping: document -> run ----

    static EvalRun fromDocument(Document d) {
        List<EvalCaseRecord> cases = new ArrayList<>();
        for (Document c : docs(d, "cases")) {
            cases.add(caseRecord(c));
        }
        Document summary = d.get("summary", Document.class);
        return new EvalRun(d.getObjectId("_id").toHexString(), d.getString("configKey"),
                num(d, "candidateVersion").intValue(), num(d, "candidateDocVersion").longValue(),
                d.getDate("candidateUpdatedAt").toInstant(),
                docs(d, "baselineVersions").stream()
                        .map(v -> new PromptVersion(v.getString("configKey"), num(v, "version").intValue()))
                        .toList(),
                d.getString("datasetHash"), EvalStatus.valueOf(d.getString("status")), d.getString("errorCode"),
                d.getString("triggeredBy"), d.getDate("startedAt").toInstant(), d.getDate("heartbeatAt").toInstant(),
                instant(d.getDate("finishedAt")), num(d, "totalCases").intValue(),
                num(d, "completedCases").intValue(), summary == null ? null : summaryRecord(summary), cases,
                num(d, "docVersion").longValue());
    }

    private static EvalSummary summaryRecord(Document s) {
        return new EvalSummary(metricsRecord(s.get("baseline", Document.class)),
                metricsRecord(s.get("candidate", Document.class)), num(s, "judgeTokens").longValue());
    }

    private static EvalMetrics metricsRecord(Document m) {
        return new EvalMetrics(num(m, "correct").intValue(), num(m, "total").intValue(),
                num(m, "toolsOk").intValue(), num(m, "refusedExpected").intValue(),
                num(m, "expectedRefusals").intValue(), num(m, "refusedNormal").intValue(),
                num(m, "normalCases").intValue(), intOrZero(m, "routeOk"), intOrZero(m, "routeCases"),
                num(m, "avgTotalTokens").longValue(),
                num(m, "avgInputTokens").longValue(), num(m, "avgOutputTokens").longValue(),
                num(m, "avgDurationMs").longValue());
    }

    private static EvalCaseRecord caseRecord(Document c) {
        return new EvalCaseRecord(c.getString("caseId"), c.getString("group"), c.getString("question"),
                c.getBoolean("expectRefusal"), c.getString("expectCategory"), resultRecord(c.get("baseline", Document.class)),
                resultRecord(c.get("candidate", Document.class)));
    }

    private static CaseResult resultRecord(Document r) {
        Document judge = r.get("judge", Document.class);
        return new CaseResult(r.getString("outcome"), r.getString("errorCode"), r.getString("answer"),
                docs(r, "toolCalls").stream()
                        .map(t -> new EvaluationReport.ToolCall(t.getString("toolName"), t.getString("status")))
                        .toList(),
                num(r, "modelCalls").intValue(), num(r, "inputTokens").intValue(),
                num(r, "outputTokens").intValue(), num(r, "totalTokens").intValue(),
                num(r, "durationMs").intValue(), strings(r, "unverifiedNumbers"), r.getBoolean("toolsOk"),
                r.getBoolean("numbersOk"), strings(r, "missingPhrases"), strings(r, "forbiddenPhrases"),
                judge == null ? null : judgeRecord(judge), r.getBoolean("passed"), r.getBoolean("routeOk"),
                r.getString("routeSource"), r.getString("instructionKey"));
    }

    private static JudgeVerdict judgeRecord(Document j) {
        return new JudgeVerdict(j.getBoolean("refused"),
                docs(j, "rubric").stream()
                        .map(x -> new JudgeVerdict.RubricResult(x.getString("text"), x.getBoolean("met"))).toList(),
                j.getString("comment"), j.getString("error"), num(j, "tokens").intValue());
    }

    private static List<Document> docs(Document d, String key) {
        List<Document> list = d.getList(key, Document.class);
        return list == null ? List.of() : list;
    }

    private static List<String> strings(Document d, String key) {
        List<String> list = d.getList(key, String.class);
        return list == null ? List.of() : List.copyOf(list);
    }

    private static Number num(Document d, String key) {
        return d.get(key, Number.class);
    }

    /** Fields added later are missing from older runs. */
    private static int intOrZero(Document d, String key) {
        Number n = num(d, key);
        return n == null ? 0 : n.intValue();
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
