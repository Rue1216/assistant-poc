package com.example.copilot.aiassistant.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.domain.chat.EvaluationReport;
import com.example.copilot.aiassistant.domain.chat.EvaluationReport.ToolCall;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseCheckerTest {

    private static EvalCase caseOf(List<String> tools, List<String> forbidden, List<String> must,
                                   List<String> mustNot) {
        return new EvalCase("c1", "g", AccessLevel.values()[0], "C1", "q",
                new EvalCase.Expect(tools, forbidden, false, must, mustNot, null), List.of());
    }

    private static EvaluationReport report(String answer, List<ToolCall> calls, List<String> unverified) {
        return new EvaluationReport("ANSWERED", null, "q", answer, calls, 1, 10, 5, 15, 100, unverified, null, null);
    }

    @Test
    void allToolsCalledAndNoForbidden() {
        EvalCase c = caseOf(List.of("a", "b"), List.of("x"), List.of(), List.of());
        assertThat(CaseChecker.check(c, report("ok", List.of(new ToolCall("a", "EXECUTED"),
                new ToolCall("b", "EXECUTED")), List.of())).toolsOk()).isTrue();
        assertThat(CaseChecker.check(c, report("ok", List.of(new ToolCall("a", "EXECUTED")), List.of()))
                .toolsOk()).isFalse();
        assertThat(CaseChecker.check(c, report("ok", List.of(new ToolCall("a", "EXECUTED"),
                new ToolCall("b", "EXECUTED"), new ToolCall("x", "DENIED")), List.of())).toolsOk()).isFalse();
        assertThat(CaseChecker.check(c, report("ok", List.of(new ToolCall("a", "EXECUTED"),
                new ToolCall("b", "EXECUTED"), new ToolCall("x", "TOOL_CALL_LIMIT_REACHED")), List.of()))
                .toolsOk()).isFalse();
    }

    @Test
    void phrases() {
        EvalCase c = caseOf(List.of(), List.of(), List.of("月配息", "面談"), List.of("王小明", "abc"));
        RuleChecks checks = CaseChecker.check(c, report("月配息 王小明 ABC", List.of(), List.of()));
        assertThat(checks.missingPhrases()).containsExactly("面談");
        assertThat(checks.forbiddenPhrases()).containsExactly("王小明");
    }

    @Test
    void numbersOkFollowsUnverifiedNumbers() {
        EvalCase c = caseOf(List.of(), List.of(), List.of(), List.of());
        assertThat(CaseChecker.check(c, report("a", List.of(), List.of())).numbersOk()).isTrue();
        assertThat(CaseChecker.check(c, report("a", List.of(), List.of("12"))).numbersOk()).isFalse();
    }

    @Test
    void failedReportFailsEverything() {
        EvalCase c = caseOf(List.of(), List.of(), List.of("月配息"), List.of("x"));
        RuleChecks checks = CaseChecker.check(c, EvaluationReport.failed("AI_LLM_TIMEOUT"));
        assertThat(checks.toolsOk()).isFalse();
        assertThat(checks.numbersOk()).isFalse();
        assertThat(checks.missingPhrases()).containsExactly("月配息");
        assertThat(checks.forbiddenPhrases()).isEmpty();
    }

    private static EvalCase routeCase(String category) {
        return new EvalCase("c1", "g", AccessLevel.values()[0], "C1", "q",
                new EvalCase.Expect(List.of(), List.of(), false, List.of(), List.of(), category), List.of());
    }

    private static EvaluationReport routed(String outcome, String source, String key) {
        return new EvaluationReport(outcome, null, "q", "ANSWERED".equals(outcome) ? "a" : null, List.of(), 1, 10, 5,
                15, 100, List.of(), source, key);
    }

    @Test
    void routeCheck() {
        assertThat(CaseChecker.check(routeCase("instruction.contact-prep"),
                routed("ANSWERED", "ROUTER", "instruction.contact-prep")).routeOk()).isTrue();
        assertThat(CaseChecker.check(routeCase("instruction.contact-prep"),
                routed("ANSWERED", "ROUTER", "instruction.opportunity")).routeOk()).isFalse();
        assertThat(CaseChecker.check(routeCase("none"), routed("ANSWERED", "ROUTER", null)).routeOk()).isTrue();
        assertThat(CaseChecker.check(routeCase("none"), routed("ANSWERED", "FALLBACK", null)).routeOk()).isFalse();
        assertThat(CaseChecker.check(routeCase("none"), routed("ANSWERED", "ROUTER", "instruction.opportunity"))
                .routeOk()).isFalse();
        assertThat(CaseChecker.check(routeCase("instruction.contact-prep"),
                routed("ANSWERED", "FALLBACK", null)).routeOk()).isFalse();
        assertThat(CaseChecker.check(routeCase("instruction.contact-prep"),
                EvaluationReport.failed("AI_LLM_TIMEOUT")).routeOk()).isFalse();
        assertThat(CaseChecker.check(routeCase(null), routed("ANSWERED", "ROUTER", "instruction.opportunity"))
                .routeOk()).isNull();
        assertThat(CaseChecker.check(routeCase(null), EvaluationReport.failed("X")).routeOk()).isNull();
    }

    @Test
    void expectedCategoryMissingFromSnapshotFailsRouteCheck() {
        // The category was removed from the candidate snapshot, so the router can only answer "none" or fall back.
        EvalCase c = routeCase("instruction.removed");
        assertThat(CaseChecker.check(c, routed("ANSWERED", "ROUTER", null)).routeOk()).isFalse();
        assertThat(CaseChecker.check(c, routed("ANSWERED", "FALLBACK", null)).routeOk()).isFalse();
    }
}
