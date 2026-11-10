package com.example.copilot.aiassistant.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.application.chat.ScriptedChatModel;
import com.example.copilot.aiassistant.domain.eval.CaseResult;
import com.example.copilot.aiassistant.domain.eval.EvalCase;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalDataset;
import com.example.copilot.aiassistant.domain.eval.EvalErrors;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import com.example.copilot.aiassistant.application.support.RequestContext;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import com.example.copilot.testsupport.LogCapture;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class EvalRunnerTest extends EvalRunnerTestBase {

    @TestConfiguration
    static class ThreeCases {
        @Bean
        @Primary
        EvalDataset threeCaseDataset() {
            return new EvalDataset(List.of(
                    new EvalCase("c1", "g", AccessLevel.FULL, TestCustomers.RETIREE, "王小明 question of c1",
                            new EvalCase.Expect(List.of(), List.of(), false, List.of(), List.of(), null),
                            List.of("rubric of c1")),
                    evalCase("c2", TestCustomers.RETIREE, AccessLevel.LIMITED, false),
                    evalCase("c3", TestCustomers.RETIREE, AccessLevel.FULL, true)), "d".repeat(64));
        }
    }

    @MockitoSpyBean EvalJudge judge;
    @MockitoSpyBean EvalRunRepository repository;
    @Autowired ToolRegistry registry;

    private EvalRun reload(EvalRun run) {
        return runs.findById(run.id()).orElseThrow();
    }

    @Test
    void runsBothSidesAndJudgesEach() {
        for (int i = 0; i < 3; i++) {
            scriptFullCase();
        }
        EvalRun run = insertRun(3, Duration.ofSeconds(1));

        runner.run(run, snapshot());

        EvalRun done = reload(run);
        assertThat(done.status()).isEqualTo(EvalStatus.COMPLETED);
        assertThat(done.errorCode()).isNull();
        assertThat(done.finishedAt()).isNotNull();
        assertThat(done.completedCases()).isEqualTo(3);
        assertThat(done.cases()).extracting(EvalCaseRecord::caseId).containsExactly("c1", "c2", "c3");
        assertThat(done.cases()).allSatisfy(c -> {
            assertThat(c.baseline().outcome()).isEqualTo("ANSWERED");
            assertThat(c.candidate().outcome()).isEqualTo("ANSWERED");
            assertThat(c.baseline().judge()).isNotNull();
            assertThat(c.candidate().judge()).isNotNull();
        });
        // The stored question is the de-identified one the model saw, never the customer's name.
        assertThat(done.cases()).extracting(EvalCaseRecord::question).containsExactly(
                PiiRedactor.SELF + " question of c1", "question of c2", "question of c3");
        assertThat(done.cases().get(2).expectRefusal()).isTrue();
        assertThat(done.summary().baseline().total()).isEqualTo(3);
        assertThat(done.summary().candidate().total()).isEqualTo(3);
        assertThat(done.summary().judgeTokens()).isEqualTo(6L * JUDGE_TOKENS);
        // Answer requests carry the prompt of their side in the system message.
        assertThat(scripted.requests()).hasSize(12);
        assertThat(scripted.requests().get(0).messages().toString()).contains("BASELINE-PROMPT");
        assertThat(scripted.requests().get(2).messages().toString()).contains("CANDIDATE-PROMPT");
    }

    @Test
    void candidateScopeReachesRouter() {
        for (int i = 0; i < 3; i++) {
            routeStep();
            answerStep();
            judgeStep();
            routeStep();
            answerStep();
            judgeStep();
        }
        EvalRun run = insertRun(3, Duration.ofSeconds(1));

        runner.run(run, snapshot(
                List.of(config("instruction.contact-prep", 1, "## 適用問題\nSCOPE-PUBLISHED\n\n## 回答方式\nx")),
                List.of(config("instruction.contact-prep", 2, "## 適用問題\nSCOPE-DRAFT\n\n## 回答方式\nx"))));

        assertThat(reload(run).status()).isEqualTo(EvalStatus.COMPLETED);
        String baselineRoute = routerInput(scripted.requests().get(0));
        String candidateRoute = routerInput(scripted.requests().get(3));
        assertThat(baselineRoute).contains("SCOPE-PUBLISHED").doesNotContain("SCOPE-DRAFT");
        assertThat(candidateRoute).contains("SCOPE-DRAFT").doesNotContain("SCOPE-PUBLISHED");
    }

    private static String routerInput(ChatRequest request) {
        return ((dev.langchain4j.data.message.UserMessage) request.messages().get(1)).singleText();
    }

    @Test
    void progressIsWrittenPerCase() {
        EvalRun run = insertRun(3, Duration.ofMinutes(5));
        AtomicReference<EvalRun> atFirstAnswer = new AtomicReference<>();
        AtomicReference<EvalRun> atSecondCase = new AtomicReference<>();
        scripted.reply(request -> {
            atFirstAnswer.set(reload(run));
            return ScriptedChatModel.answer(ANSWER_TOKENS, "這是回答");
        });
        judgeStep();
        answerStep();
        judgeStep();
        scripted.reply(request -> {
            atSecondCase.set(reload(run));
            return ScriptedChatModel.answer(ANSWER_TOKENS, "這是回答");
        });
        judgeStep();
        answerStep();
        judgeStep();
        scriptFullCase();

        runner.run(run, snapshot());

        // The heartbeat is refreshed when the runner starts (the run may have waited in the executor queue).
        assertThat(atFirstAnswer.get().heartbeatAt()).isAfter(run.heartbeatAt());
        assertThat(atFirstAnswer.get().docVersion()).isEqualTo(1L);
        assertThat(atFirstAnswer.get().completedCases()).isZero();
        // Case 1 is stored before case 2 starts.
        assertThat(atSecondCase.get().completedCases()).isEqualTo(1);
        // Millisecond clock: two writes may share a tick, so only "not older" is guaranteed.
        assertThat(atSecondCase.get().heartbeatAt()).isAfterOrEqualTo(atFirstAnswer.get().heartbeatAt());
        assertThat(atSecondCase.get().docVersion()).isEqualTo(2L);
        assertThat(atSecondCase.get().status()).isEqualTo(EvalStatus.RUNNING);
    }

    @Test
    void failedAnswerIsNotJudged() {
        // A model timeout may be one slow case: it fails the case, not the run.
        scripted.fail(new TimeoutException("slow"));
        answerStep();
        judgeStep();
        scriptFullCase();
        scriptFullCase();
        EvalRun run = insertRun(3, Duration.ofSeconds(1));

        runner.run(run, snapshot());

        EvalRun done = reload(run);
        assertThat(done.status()).isEqualTo(EvalStatus.COMPLETED);
        CaseResult baseline = done.cases().get(0).baseline();
        assertThat(baseline.outcome()).isEqualTo("FAILED");
        assertThat(baseline.errorCode()).isEqualTo("AI_LLM_TIMEOUT");
        assertThat(baseline.judge()).isNull();
        assertThat(baseline.passed()).isFalse();
        assertThat(done.cases().get(0).candidate().judge()).isNotNull();
        assertThat(scripted.requests()).hasSize(11);
        assertThat(done.summary().judgeTokens()).isEqualTo(5L * JUDGE_TOKENS);
    }

    @Test
    void unavailableModelStopsRun() {
        // Unclassified model failures (connection refused, bad key, unknown model) map to AI_LLM_UNAVAILABLE.
        scripted.fail(new IllegalStateException("connection refused"));
        answerStep();
        judgeStep();
        EvalRun run = insertRun(3, Duration.ofSeconds(1));

        runner.run(run, snapshot());

        EvalRun done = reload(run);
        assertThat(done.status()).isEqualTo(EvalStatus.FAILED);
        assertThat(done.errorCode()).isEqualTo(EvalErrors.LLM_UNAVAILABLE);
        assertThat(done.finishedAt()).isNotNull();
        assertThat(done.summary()).isNull();
        // The case that hit the failure is kept; case 2 is never asked.
        assertThat(done.cases()).extracting(EvalCaseRecord::caseId).containsExactly("c1");
        assertThat(done.cases().get(0).baseline().errorCode()).isEqualTo("AI_LLM_UNAVAILABLE");
        assertThat(scripted.requests()).hasSize(3);
    }

    @Test
    void rateLimitedModelStopsRun() {
        answerStep();
        judgeStep();
        scripted.fail(new RateLimitException("429"));
        EvalRun run = insertRun(3, Duration.ofSeconds(1));

        runner.run(run, snapshot());

        EvalRun done = reload(run);
        assertThat(done.status()).isEqualTo(EvalStatus.FAILED);
        assertThat(done.errorCode()).isEqualTo(EvalErrors.LLM_UNAVAILABLE);
        assertThat(done.cases()).hasSize(1);
        assertThat(done.cases().get(0).candidate().errorCode()).isEqualTo("AI_LLM_RATE_LIMITED");
        assertThat(scripted.requests()).hasSize(3);
    }

    @Test
    void limitedCaseGetsLimitedTools() {
        for (int i = 0; i < 3; i++) {
            scriptFullCase();
        }
        EvalRun run = insertRun(3, Duration.ofSeconds(1));

        runner.run(run, snapshot());

        List<String> limited = registry.toolsFor(AccessLevel.LIMITED).stream().map(t -> t.name()).sorted().toList();
        List<String> full = registry.toolsFor(AccessLevel.FULL).stream().map(t -> t.name()).sorted().toList();
        assertThat(limited).isNotEqualTo(full);
        List<ChatRequest> requests = scripted.requests();
        assertThat(toolNames(requests.get(0))).isEqualTo(full);
        // Case 2: requests 4 (baseline answer) and 6 (candidate answer).
        assertThat(toolNames(requests.get(4))).isEqualTo(limited);
        assertThat(toolNames(requests.get(6))).isEqualTo(limited);
        assertThat(toolNames(requests.get(8))).isEqualTo(full);
    }

    private static List<String> toolNames(ChatRequest request) {
        return request.toolSpecifications().stream().map(ToolSpecification::name).sorted().toList();
    }

    @Test
    void timeoutStopsRun() {
        // Started longer ago than the run timeout (30 minutes by default).
        EvalRun run = insertRun(3, Duration.ofHours(2));

        runner.run(run, snapshot());

        EvalRun done = reload(run);
        assertThat(done.status()).isEqualTo(EvalStatus.FAILED);
        assertThat(done.errorCode()).isEqualTo(EvalErrors.TIMEOUT);
        assertThat(done.finishedAt()).isNotNull();
        assertThat(scripted.requests()).isEmpty();
    }

    @Test
    void stopsWhenRunWasInterrupted() {
        EvalRun run = insertRun(3, Duration.ofSeconds(1));
        scripted.reply(request -> {
            EvalRun current = reload(run);
            assertThat(runs.fail(run.id(), current.docVersion(), EvalErrors.INTERRUPTED, clock.instant())).isTrue();
            return ScriptedChatModel.answer(ANSWER_TOKENS, "這是回答");
        });
        judgeStep();
        answerStep();
        judgeStep();

        runner.run(run, snapshot());

        EvalRun done = reload(run);
        assertThat(done.status()).isEqualTo(EvalStatus.FAILED);
        assertThat(done.errorCode()).isEqualTo(EvalErrors.INTERRUPTED);
        assertThat(done.cases()).isEmpty();
        // The case in progress finishes, then nothing more is written or asked.
        assertThat(scripted.requests()).hasSize(4);
    }

    @Test
    void doesNotStartWhenAlreadyInterrupted() {
        EvalRun run = insertRun(3, Duration.ofSeconds(1));
        assertThat(runs.fail(run.id(), run.docVersion(), EvalErrors.INTERRUPTED, clock.instant())).isTrue();

        runner.run(run, snapshot());

        assertThat(scripted.requests()).isEmpty();
        assertThat(reload(run).errorCode()).isEqualTo(EvalErrors.INTERRUPTED);
    }

    @Test
    void unexpectedErrorMarksFailed() {
        scriptFullCase();
        answerStep();
        // Case 1 judged normally (two calls); the baseline judge of case 2 blows up.
        doCallRealMethod().doCallRealMethod().doThrow(new IllegalStateException("judge exploded"))
                .when(judge).judge(any(), any(), any(), any());
        EvalRun run = insertRun(3, Duration.ofSeconds(1));

        runner.run(run, snapshot());

        EvalRun done = reload(run);
        assertThat(done.status()).isEqualTo(EvalStatus.FAILED);
        assertThat(done.errorCode()).isEqualTo("AI_INTERNAL_ERROR");
        assertThat(done.completedCases()).isEqualTo(1);
        assertThat(done.cases()).hasSize(1);
    }

    @Test
    void unexpectedErrorAfterInterruptionLogsInterrupted() {
        EvalRun run = insertRun(3, Duration.ofSeconds(1));
        answerStep();
        // The run is marked interrupted meanwhile, then the judge blows up: the fail write is refused.
        doAnswer(inv -> {
            assertThat(runs.fail(run.id(), reload(run).docVersion(), EvalErrors.INTERRUPTED, clock.instant()))
                    .isTrue();
            throw new IllegalStateException("judge exploded");
        }).when(judge).judge(any(), any(), any(), any());
        List<String> lines;
        try (LogCapture capture = LogCapture.of(EvalRunner.class, Level.INFO)) {
            runner.run(run, snapshot());
            lines = capture.events().stream().map(LogCapture::render).toList();
        }

        assertThat(reload(run).errorCode()).isEqualTo(EvalErrors.INTERRUPTED);
        assertThat(lines).anyMatch(l -> l.startsWith("eval run finished runId=" + run.id() + " status=INTERRUPTED "));
    }

    @Test
    void interruptedThreadDoesNotStart() {
        EvalRun run = insertRun(3, Duration.ofSeconds(1));
        List<String> lines;
        // shutdownNow interrupts the runner thread; the test plays that thread.
        Thread.currentThread().interrupt();
        try (LogCapture capture = LogCapture.of(EvalRunner.class, Level.INFO)) {
            runner.run(run, snapshot());
            lines = capture.events().stream().map(LogCapture::render).toList();
        } finally {
            Thread.interrupted();
        }

        assertThat(scripted.requests()).isEmpty();
        EvalRun after = reload(run);
        // Nothing is written: the stale check later marks the run AI_EVAL_INTERRUPTED.
        assertThat(after.status()).isEqualTo(EvalStatus.RUNNING);
        assertThat(after.docVersion()).isZero();
        assertThat(after.cases()).isEmpty();
        assertThat(lines).contains("eval run stopped runId=" + run.id() + " reason=interrupted");
        assertThat(lines).anyMatch(l -> l.startsWith("eval run finished runId=" + run.id() + " status=INTERRUPTED "));
    }

    @Test
    void interruptedThreadStopsBeforeTheNextCase() {
        scriptFullCase();
        EvalRun run = insertRun(3, Duration.ofSeconds(1));
        // The interrupt arrives while case 1 is being stored (blocking socket I/O does not notice it).
        doAnswer(inv -> {
            Object written = inv.callRealMethod();
            Thread.currentThread().interrupt();
            return written;
        }).when(repository).appendCase(any(), anyLong(), any(), any());
        List<String> lines;
        try (LogCapture capture = LogCapture.of(EvalRunner.class, Level.INFO)) {
            runner.run(run, snapshot());
            lines = capture.events().stream().map(LogCapture::render).toList();
        } finally {
            Thread.interrupted();
        }

        // Case 1 is kept; case 2 is never asked and nothing more is written.
        assertThat(scripted.requests()).hasSize(4);
        EvalRun after = reload(run);
        assertThat(after.status()).isEqualTo(EvalStatus.RUNNING);
        assertThat(after.cases()).extracting(EvalCaseRecord::caseId).containsExactly("c1");
        assertThat(lines).contains("eval run stopped runId=" + run.id() + " reason=interrupted");
        assertThat(lines).anyMatch(l -> l.startsWith("eval run finished runId=" + run.id() + " status=INTERRUPTED "));
    }

    @Test
    void logsCountersOnlyAndSetsRequestId() {
        AtomicReference<String> during = new AtomicReference<>();
        AtomicReference<String> duringJudge = new AtomicReference<>();
        scripted.reply(request -> {
            during.set(MDC.get(RequestContext.MDC_KEY));
            return ScriptedChatModel.answer(ANSWER_TOKENS, "這是回答");
        });
        scripted.reply(request -> {
            duringJudge.set(MDC.get(RequestContext.MDC_KEY));
            return ScriptedChatModel.text("{\"refused\":false,\"rubric\":[{\"index\":1,\"met\":true}],"
                    + "\"comment\":\"評語內容\"}", JUDGE_TOKENS);
        });
        answerStep();
        judgeStep();
        scriptFullCase();
        scriptFullCase();
        EvalRun run = insertRun(3, Duration.ofSeconds(1));
        MDC.put(RequestContext.MDC_KEY, "outer");
        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(EvalRunner.class, Level.INFO)) {
            runner.run(run, snapshot());
            events = capture.events();
        } finally {
            // Restored to what the caller had.
            assertThat(MDC.get(RequestContext.MDC_KEY)).isEqualTo("outer");
            MDC.remove(RequestContext.MDC_KEY);
        }

        assertThat(during.get()).isEqualTo("e" + run.id().substring(run.id().length() - 7));
        // The judge call runs under the same request id, so its failure lines can be tied to the run.
        assertThat(duringJudge.get()).isEqualTo(during.get());
        List<String> lines = events.stream().map(LogCapture::render).toList();
        assertThat(lines.stream().filter(l -> l.startsWith("eval case done"))).hasSize(6);
        assertThat(lines).anyMatch(l -> l.startsWith("eval case done runId=" + run.id() + " case=c1 side=baseline "
                + "outcome=ANSWERED totalTokens=" + ANSWER_TOKENS + " durationMs="));
        assertThat(lines).anyMatch(l -> l.startsWith("eval run finished runId=" + run.id() + " status=COMPLETED ")
                && l.contains("chatTokens=" + 6 * ANSWER_TOKENS) && l.contains("judgeTokens=" + 6 * JUDGE_TOKENS));
        assertThat(String.join("\n", lines)).doesNotContain("question of").doesNotContain("這是回答")
                .doesNotContain("評語內容").doesNotContain("rubric of");
    }
}
