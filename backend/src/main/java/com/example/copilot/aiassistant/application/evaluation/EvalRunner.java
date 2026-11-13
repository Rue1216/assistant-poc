package com.example.copilot.aiassistant.application.evaluation;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.application.command.evaluation.EvalRunCommandUseCase;
import com.example.copilot.aiassistant.application.command.chat.ChatUseCase;
import com.example.copilot.aiassistant.domain.chat.EvaluationReport;
import com.example.copilot.aiassistant.domain.customer.CertNo;
import com.example.copilot.aiassistant.domain.eval.CaseChecker;
import com.example.copilot.aiassistant.domain.eval.CaseResult;
import com.example.copilot.aiassistant.domain.eval.EvalCase;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalDataset;
import com.example.copilot.aiassistant.domain.eval.EvalErrors;
import com.example.copilot.aiassistant.domain.eval.EvalMetrics;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalSummary;
import com.example.copilot.aiassistant.domain.eval.JudgeVerdict;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import com.example.copilot.aiassistant.application.support.RequestContext;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.application.port.CustomerAccessPort;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.user.UserContext;
import com.example.copilot.aiassistant.application.chat.CustomerContextFactory;
import com.example.copilot.aiassistant.domain.config.PublishedConfigs;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Executes one evaluation run case by case on the evaluation executor (prompt-evaluation spec 4.2): baseline answer,
 * its judgement, candidate answer, its judgement, then one write per case. Every write is conditional on the run's
 * {@code docVersion}; a refused write means the run was marked interrupted and the runner stops. Never throws.
 */
@Component
@Slf4j
public class EvalRunner {

    private static final String INTERNAL_ERROR = "AI_INTERNAL_ERROR";
    private static final String CUSTOMER_NOT_FOUND = "AI_CUSTOMER_NOT_FOUND";
    private static final Set<String> LLM_DOWN = Set.of(ErrorCode.AI_LLM_UNAVAILABLE.name(),
            ErrorCode.AI_LLM_RATE_LIMITED.name());
    private static final UserContext EVAL_USER = new UserContext("eval-runner", "評測", "EVAL", Set.of());

    private final ChatUseCase orchestrator;
    private final CustomerContextFactory customerFactory;
    private final CustomerAccessPort accessPort;
    private final EvalJudge judge;
    private final EvalRunRepository repository;
    private final EvalDataset dataset;
    private final Clock clock;
    private final Duration runTimeout;

    public EvalRunner(ChatUseCase orchestrator, CustomerContextFactory customerFactory, CustomerAccessPort accessPort, EvalJudge judge,
                      EvalRunRepository repository, EvalDataset dataset, Clock clock,
                      AiAssistantProperties properties) {
        this.orchestrator = orchestrator;
        this.customerFactory = customerFactory;
        this.accessPort = accessPort;
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

    public void run(EvalRun run, EvalRunCommandUseCase.EvalSnapshot snapshot) {
        Instant began = clock.instant();
        Progress progress = new Progress();
        progress.docVersion = run.docVersion();
        String status = "FAILED";
        // The whole run, including its summary line, is logged under the run id as trace id, with a fresh span.
        String previousTrace = MDC.get(RequestContext.MDC_KEY);
        String previousSpan = MDC.get(RequestContext.SPAN_KEY);
        MDC.put(RequestContext.MDC_KEY, run.id());
        MDC.put(RequestContext.SPAN_KEY, RequestContext.newId());
        try {
            try {
                if (interrupted(run)) {
                    status = "INTERRUPTED";
                    return;
                }
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
                status = fail(run, progress, INTERNAL_ERROR) ? "FAILED" : "INTERRUPTED";
            } finally {
                log.info("eval run finished runId={} status={} durationMs={} chatTokens={} judgeTokens={}", run.id(),
                        status, Duration.between(began, clock.instant()).toMillis(), progress.chatTokens,
                        progress.judgeTokens);
            }
        } finally {
            restore(RequestContext.MDC_KEY, previousTrace);
            restore(RequestContext.SPAN_KEY, previousSpan);
        }
    }

    /**
     * Returns how the run ended: COMPLETED, FAILED (timeout, or the model unavailable after a case) or INTERRUPTED (a conditional write was refused, or the
     * thread was interrupted).
     */
    private String execute(EvalRun run, EvalRunCommandUseCase.EvalSnapshot snapshot, Progress progress) {
        Instant deadline = run.startedAt().plus(runTimeout);
        List<EvalCaseRecord> records = new ArrayList<>();
        for (EvalCase c : dataset.cases()) {
            if (interrupted(run)) {
                return "INTERRUPTED";
            }
            if (!clock.instant().isBefore(deadline)) {
                return fail(run, progress, EvalErrors.TIMEOUT) ? "FAILED" : "INTERRUPTED";
            }
            Optional<CustomerContext> customer = customerFor(run, c);
            Side baseline = side(run, c, "baseline", snapshot.baseline(), customer, progress);
            Side candidate = side(run, c, "candidate", snapshot.candidate(), customer, progress);
            EvalCaseRecord record = new EvalCaseRecord(c.id(), c.group(),
                    storedQuestion(c, baseline.report(), candidate.report()), c.expect().refusal(),
                    c.expect().category(), baseline.result(), candidate.result());
            if (!repository.appendCase(run.id(), progress.docVersion, record, clock.instant())) {
                return "INTERRUPTED";
            }
            progress.docVersion++;
            records.add(record);
            // Otherwise every later case fails the same way and the summary reads as "no difference".
            if (modelUnavailable(baseline.report()) || modelUnavailable(candidate.report())) {
                log.warn("eval run stopped runId={} reason=llm-unavailable", run.id());
                return fail(run, progress, EvalErrors.LLM_UNAVAILABLE) ? "FAILED" : "INTERRUPTED";
            }
        }
        EvalSummary summary = new EvalSummary(EvalMetrics.of(records, false), EvalMetrics.of(records, true),
                progress.judgeTokens);
        if (!repository.complete(run.id(), progress.docVersion, summary, clock.instant())) {
            return "INTERRUPTED";
        }
        progress.docVersion++;
        return "COMPLETED";
    }

    /**
     * The case's customer, or empty when it does not exist or the user who started the run has no access to it:
     * forEvaluation does not check access, so this is the defense in depth against a dataset naming such a customer.
     */
    private Optional<CustomerContext> customerFor(EvalRun run, EvalCase c) {
        UserContext trigger = new UserContext(run.triggeredBy(), run.triggeredBy(), "EVAL", Set.of());
        if (accessPort.checkAccess(trigger, c.certNo()) == AccessLevel.NONE) {
            return Optional.empty();
        }
        return customerFactory.forEvaluation(new CertNo(c.certNo()), c.accessLevel());
    }

    /** One side's stored result and the report it was built from. */
    private record Side(CaseResult result, EvaluationReport report) {
    }

    /** Answers the case with one config set and judges the answer, but only when there is one. */
    private Side side(EvalRun run, EvalCase c, String side, PublishedConfigs configs,
                      Optional<CustomerContext> customer, Progress progress) {
        EvaluationReport report;
        JudgeVerdict verdict = null;
        if (customer.isEmpty()) {
            report = EvaluationReport.failed(CUSTOMER_NOT_FOUND);
        } else {
            // The turn and its judgement log under the run's trace id, which run(...) has already put in the MDC.
            report = orchestrator.evaluate(c.question(), EVAL_USER, customer.get(), configs);
            if ("ANSWERED".equals(report.outcome())) {
                verdict = judge.judge(report.redactedQuestion(), report.answer(), c.rubric(), customer.get());
                progress.judgeTokens += verdict.tokens();
            }
        }
        progress.chatTokens += report.totalTokens();
        CaseResult result = CaseResult.of(report, CaseChecker.check(c, report), verdict, c.expect().refusal());
        log.info("eval case done runId={} case={} side={} outcome={} totalTokens={} durationMs={} passed={}",
                run.id(), c.id(), side, result.outcome(), result.totalTokens(), result.durationMs(),
                result.passed());
        return new Side(result, report);
    }

    /**
     * The de-identified question the model saw (spec 5.2); the baseline's when it has one. When neither side reached
     * the model, the dataset question is kept: it is a fixed string about a test customer.
     */
    private static String storedQuestion(EvalCase c, EvaluationReport baseline, EvaluationReport candidate) {
        if (baseline.redactedQuestion() != null) {
            return baseline.redactedQuestion();
        }
        return candidate.redactedQuestion() != null ? candidate.redactedQuestion() : c.question();
    }

    /** True when the turn failed because the model could not be reached or refused for load; a timeout may be one slow case. */
    private static boolean modelUnavailable(EvaluationReport report) {
        // Set.of rejects contains(null), and an answered turn has no error code.
        return report.errorCode() != null && LLM_DOWN.contains(report.errorCode());
    }

    /** True when the runner thread was interrupted (executor shutdown); the stale check then ends the run. */
    private static boolean interrupted(EvalRun run) {
        if (!Thread.currentThread().isInterrupted()) {
            return false;
        }
        log.warn("eval run stopped runId={} reason=interrupted", run.id());
        return true;
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previous);
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
