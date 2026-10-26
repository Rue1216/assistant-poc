package com.example.copilot.aiassistant.evaluation;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.chat.ChatOrchestrator;
import com.example.copilot.aiassistant.chat.EvaluationReport;
import com.example.copilot.aiassistant.common.RequestIdFilter;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.privacy.CustomerContextFactory;
import com.example.copilot.aiassistant.prompt.AssembledPrompt;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Executes one evaluation run case by case on the evaluation executor (prompt-evaluation spec 4.2): baseline answer,
 * its judgement, candidate answer, its judgement, then one write per case. Every write is conditional on the run's
 * {@code docVersion}; a refused write means the run was marked interrupted and the runner stops. Never throws.
 */
@Component
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);
    private static final String INTERNAL_ERROR = "AI_INTERNAL_ERROR";
    private static final String CUSTOMER_NOT_FOUND = "AI_CUSTOMER_NOT_FOUND";
    private static final UserContext EVAL_USER = new UserContext("eval-runner", "評測", "EVAL", Set.of());

    private final ChatOrchestrator orchestrator;
    private final CustomerContextFactory customerFactory;
    private final EvalJudge judge;
    private final EvalRunRepository repository;
    private final EvalDataset dataset;
    private final Clock clock;
    private final Duration runTimeout;

    public EvalRunner(ChatOrchestrator orchestrator, CustomerContextFactory customerFactory, EvalJudge judge,
                      EvalRunRepository repository, EvalDataset dataset, Clock clock,
                      AiAssistantProperties properties) {
        this.orchestrator = orchestrator;
        this.customerFactory = customerFactory;
        this.judge = judge;
        this.repository = repository;
        this.dataset = dataset;
        this.clock = clock;
        this.runTimeout = properties.eval().runTimeout();
    }

    /** Version and token bookkeeping of one run; {@code docVersion} follows every successful conditional write. */
    private static final class Progress {
        long docVersion;
        long chatTokens;
        long judgeTokens;
    }

    public void run(EvalRun run, EvalRunService.EvalSnapshot snapshot) {
        Instant began = clock.instant();
        Progress progress = new Progress();
        progress.docVersion = run.docVersion();
        String status = "FAILED";
        try {
            // A queued run's heartbeat is as old as its insert; do not go on if it was marked stale meanwhile.
            if (!repository.touch(run.id(), progress.docVersion, clock.instant())) {
                status = "INTERRUPTED";
                return;
            }
            progress.docVersion++;
            status = execute(run, snapshot, progress);
        } catch (RuntimeException e) {
            // Class name only: the message may echo question, answer or customer data.
            log.error("eval run failed runId={} error={}", run.id(), e.getClass().getSimpleName());
            fail(run, progress, INTERNAL_ERROR);
        } finally {
            log.info("eval run finished runId={} status={} durationMs={} chatTokens={} judgeTokens={}", run.id(),
                    status, Duration.between(began, clock.instant()).toMillis(), progress.chatTokens,
                    progress.judgeTokens);
        }
    }

    /** Returns how the run ended: COMPLETED, FAILED (timeout) or INTERRUPTED (a conditional write was refused). */
    private String execute(EvalRun run, EvalRunService.EvalSnapshot snapshot, Progress progress) {
        Instant deadline = run.startedAt().plus(runTimeout);
        List<EvalCaseRecord> records = new ArrayList<>();
        for (EvalCase c : dataset.cases()) {
            if (!clock.instant().isBefore(deadline)) {
                return fail(run, progress, EvalErrors.TIMEOUT) ? "FAILED" : "INTERRUPTED";
            }
            Optional<CustomerContext> customer = customerFactory.forEvaluation(c.customerId(), c.accessLevel());
            CaseResult baseline = side(run, c, "baseline", snapshot.baseline(), customer, progress);
            CaseResult candidate = side(run, c, "candidate", snapshot.candidate(), customer, progress);
            EvalCaseRecord record = new EvalCaseRecord(c.id(), c.group(), c.question(), c.expect().refusal(),
                    baseline, candidate);
            if (!repository.appendCase(run.id(), progress.docVersion, record, clock.instant())) {
                return "INTERRUPTED";
            }
            progress.docVersion++;
            records.add(record);
        }
        EvalSummary summary = new EvalSummary(EvalMetrics.of(records, false), EvalMetrics.of(records, true),
                progress.judgeTokens);
        if (!repository.complete(run.id(), progress.docVersion, summary, clock.instant())) {
            return "INTERRUPTED";
        }
        progress.docVersion++;
        return "COMPLETED";
    }

    /** Answers the case with one prompt and judges the answer, but only when there is one. */
    private CaseResult side(EvalRun run, EvalCase c, String side, AssembledPrompt prompt,
                            Optional<CustomerContext> customer, Progress progress) {
        EvaluationReport report = customer.isEmpty() ? EvaluationReport.failed(CUSTOMER_NOT_FOUND)
                : evaluate(run, c, prompt, customer.get());
        JudgeVerdict verdict = null;
        if ("ANSWERED".equals(report.outcome())) {
            verdict = judge.judge(report.redactedQuestion(), report.answer(), c.rubric(), customer.get());
            progress.judgeTokens += verdict.tokens();
        }
        progress.chatTokens += report.totalTokens();
        CaseResult result = CaseResult.of(report, CaseChecker.check(c, report), verdict, c.expect().refusal());
        log.info("eval case done runId={} case={} side={} outcome={} totalTokens={} durationMs={} passed={}",
                run.id(), c.id(), side, result.outcome(), result.totalTokens(), result.durationMs(),
                result.passed());
        return result;
    }

    /** The turn runs under a request id derived from the run, so its log lines can be tied back to it. */
    private EvaluationReport evaluate(EvalRun run, EvalCase c, AssembledPrompt prompt, CustomerContext customer) {
        String previous = MDC.get(RequestIdFilter.MDC_KEY);
        MDC.put(RequestIdFilter.MDC_KEY, "e" + run.id().substring(run.id().length() - 7));
        try {
            return orchestrator.evaluate(c.question(), EVAL_USER, customer, prompt);
        } finally {
            if (previous == null) {
                MDC.remove(RequestIdFilter.MDC_KEY);
            } else {
                MDC.put(RequestIdFilter.MDC_KEY, previous);
            }
        }
    }

    private boolean fail(EvalRun run, Progress progress, String errorCode) {
        try {
            boolean written = repository.fail(run.id(), progress.docVersion, errorCode, clock.instant());
            if (written) {
                progress.docVersion++;
            }
            return written;
        } catch (RuntimeException e) {
            log.error("eval run fail-write failed runId={} error={}", run.id(), e.getClass().getSimpleName());
            return false;
        }
    }
}
