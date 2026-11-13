package com.example.copilot.aiassistant.application.query.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.domain.chat.EvaluationReport;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import com.example.copilot.aiassistant.domain.eval.CaseResult;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalErrors;
import com.example.copilot.aiassistant.domain.eval.EvalMetrics;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import com.example.copilot.aiassistant.domain.eval.EvalSummary;
import com.example.copilot.aiassistant.domain.eval.JudgeVerdict;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import com.example.copilot.aiassistant.infrastructure.mongo.AiAssistantCollections;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

class EvalRunQueryServiceTest extends IntegrationTestBase {

    private static final ConfigKey KEY = new ConfigKey("instruction.contact-prep");

    @Autowired EvalRunQueryService service;
    @Autowired EvalRunRepository runs;
    @Autowired MongoTemplate mongo;
    @Autowired Clock clock;

    @BeforeEach
    void setUp() {
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), AiAssistantCollections.EVAL_RUN);
    }

    @AfterEach
    void tearDown() {
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), AiAssistantCollections.EVAL_RUN);
    }

    private EvalRun running(Instant heartbeat) {
        Instant now = clock.instant();
        return new EvalRun(null, KEY, 2, 0L, now, List.of(new PromptVersion(KEY.value(), 1)), "h".repeat(64),
                EvalStatus.RUNNING, null, "someone", now.minus(Duration.ofMinutes(30)), heartbeat, null, 16, 3, null,
                List.of(), 0L);
    }

    private static AssistantException thrown(Runnable r) {
        try {
            r.run();
        } catch (AssistantException e) {
            return e;
        }
        throw new AssertionError("expected AssistantException");
    }

    @Test
    void freshRunIsNotInterrupted() {
        String fresh = runs.insert(running(clock.instant().minus(Duration.ofMinutes(9))));

        assertThat(service.current()).hasValueSatisfying(r -> assertThat(r.id()).isEqualTo(fresh));
        assertThat(service.get(fresh).status()).isEqualTo(EvalStatus.RUNNING);
    }

    @Test
    void getUnknownRunIsNotFound() {
        assertThat(thrown(() -> service.get("64b7f0f0f0f0f0f0f0f0f0f0")).code())
                .isEqualTo(ErrorCode.AI_EVAL_NOT_FOUND);
        assertThat(thrown(() -> service.get("not-an-id")).code()).isEqualTo(ErrorCode.AI_EVAL_NOT_FOUND);
    }

    private static EvalCaseRecord caseRecord(String id) {
        CaseResult side = new CaseResult("ANSWERED", null, "answer", List.of(new EvaluationReport.ToolCall("t", "SUCCESS")),
                2, 100, 50, 150, 1200, List.of("123"), true, true, List.of(), List.of(),
                new JudgeVerdict(false, List.of(new JudgeVerdict.RubricResult("r", true)), "ok", null, 40), true,
                true, "ROUTER", "instruction.contact-prep");
        return new EvalCaseRecord(id, "g", "question?", false, "instruction.contact-prep", side, side);
    }

    @Test
    void repositoryWritesAreGuardedByDocVersion() {
        Instant now = clock.instant();
        Instant later = now.plusSeconds(60);
        String id = runs.insert(running(now.minus(Duration.ofMinutes(1))));

        assertThat(runs.appendCase(id, 5L, caseRecord("A1"), later)).isFalse();
        EvalRun unchanged = runs.findById(id).orElseThrow();
        assertThat(unchanged.completedCases()).isEqualTo(3);
        assertThat(unchanged.cases()).isEmpty();
        assertThat(unchanged.docVersion()).isZero();

        assertThat(runs.appendCase(id, 0L, caseRecord("A1"), later)).isTrue();
        EvalRun appended = runs.findById(id).orElseThrow();
        assertThat(appended.completedCases()).isEqualTo(4);
        assertThat(appended.docVersion()).isEqualTo(1L);
        assertThat(appended.heartbeatAt().toEpochMilli()).isEqualTo(later.toEpochMilli());
        assertThat(appended.cases()).containsExactly(caseRecord("A1"));

        EvalMetrics m = new EvalMetrics(1, 1, 1, 0, 0, 0, 1, 1, 1, 150, 100, 50, 1200);
        EvalSummary summary = new EvalSummary(m, m, 80);
        assertThat(runs.complete(id, 0L, summary, later)).isFalse();
        assertThat(runs.complete(id, 1L, summary, later)).isTrue();
        EvalRun done = runs.findById(id).orElseThrow();
        assertThat(done.status()).isEqualTo(EvalStatus.COMPLETED);
        assertThat(done.summary()).isEqualTo(summary);
        assertThat(done.finishedAt().toEpochMilli()).isEqualTo(later.toEpochMilli());
        assertThat(done.docVersion()).isEqualTo(2L);
        // A finished run accepts no more writes.
        assertThat(runs.fail(id, 2L, EvalErrors.TIMEOUT, later)).isFalse();
    }

    @Test
    void recentOmitsCasesAndIsNewestFirst() {
        Instant now = clock.instant();
        String older = runs.insert(running(now));
        runs.fail(older, 0L, EvalErrors.TIMEOUT, now);
        EvalRun newer = running(now);
        String newerId = runs.insert(new EvalRun(null, KEY, 3, 0L, now, newer.baselineVersions(), newer.datasetHash(),
                EvalStatus.RUNNING, null, "x", now.plusSeconds(5), now, null, 16, 0, null, List.of(), 0L));
        runs.appendCase(newerId, 0L, caseRecord("A1"), now);

        List<EvalRun> recent = service.recent(KEY);

        assertThat(recent).extracting(EvalRun::id).containsExactly(newerId, older);
        assertThat(recent).allSatisfy(r -> assertThat(r.cases()).isEmpty());
        assertThat(recent.get(0).completedCases()).isEqualTo(1);
        assertThat(service.recent(new ConfigKey("instruction.other"))).isEmpty();
    }
}
