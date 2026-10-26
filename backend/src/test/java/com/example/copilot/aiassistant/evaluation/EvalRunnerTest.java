package com.example.copilot.aiassistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.example.copilot.aiassistant.chat.ScriptedChatModel;
import com.example.copilot.aiassistant.common.RequestIdFilter;
import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.tool.ToolRegistry;
import com.example.copilot.testsupport.LogCapture;
import dev.langchain4j.agent.tool.ToolSpecification;
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
                    evalCase("c1", "C001", AccessLevel.FULL, false),
                    evalCase("c2", "C001", AccessLevel.LIMITED, false),
                    evalCase("c3", "C001", AccessLevel.FULL, true)), "d".repeat(64));
        }
    }

    @MockitoSpyBean EvalJudge judge;
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
            assertThat(c.question()).isEqualTo("question of " + c.caseId());
        });
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
        assertThat(atSecondCase.get().heartbeatAt()).isAfter(atFirstAnswer.get().heartbeatAt());
        assertThat(atSecondCase.get().status()).isEqualTo(EvalStatus.RUNNING);
    }

    @Test
    void failedAnswerIsNotJudged() {
        scripted.fail(new IllegalStateException("boom"));
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
        assertThat(baseline.judge()).isNull();
        assertThat(baseline.passed()).isFalse();
        assertThat(done.cases().get(0).candidate().judge()).isNotNull();
        assertThat(scripted.requests()).hasSize(11);
        assertThat(done.summary().judgeTokens()).isEqualTo(5L * JUDGE_TOKENS);
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
    void logsCountersOnlyAndSetsRequestId() {
        AtomicReference<String> during = new AtomicReference<>();
        scripted.reply(request -> {
            during.set(MDC.get(RequestIdFilter.MDC_KEY));
            return ScriptedChatModel.answer(ANSWER_TOKENS, "這是回答");
        });
        judgeStep();
        answerStep();
        judgeStep();
        scriptFullCase();
        scriptFullCase();
        EvalRun run = insertRun(3, Duration.ofSeconds(1));
        MDC.put(RequestIdFilter.MDC_KEY, "outer");
        List<LogEvent> events;
        try (LogCapture capture = LogCapture.of(EvalRunner.class, Level.INFO)) {
            runner.run(run, snapshot());
            events = capture.events();
        } finally {
            // Restored to what the caller had.
            assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("outer");
            MDC.remove(RequestIdFilter.MDC_KEY);
        }

        assertThat(during.get()).isEqualTo("e" + run.id().substring(run.id().length() - 7));
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
