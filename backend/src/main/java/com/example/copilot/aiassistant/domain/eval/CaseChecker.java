package com.example.copilot.aiassistant.domain.eval;

import com.example.copilot.aiassistant.domain.chat.EvaluationReport;
import java.util.List;

/** Pure rule checks of one answer against the case expectations (prompt-evaluation spec 4.4). */
public final class CaseChecker {

    private CaseChecker() {
    }

    public static RuleChecks check(EvalCase c, EvaluationReport r) {
        EvalCase.Expect e = c.expect();
        Boolean routeOk = routeCheck(e.category(), r);
        if (!"ANSWERED".equals(r.outcome()) || r.answer() == null) {
            // A turn without an answer fails every check; nothing in it can be forbidden.
            return new RuleChecks(false, false, e.mustContain(), List.of(), routeOk);
        }
        // Any requested call counts, including denied ones and calls over the per-question limit.
        List<String> called = r.toolCalls().stream().map(EvaluationReport.ToolCall::toolName).toList();
        boolean toolsOk = called.containsAll(e.tools()) && e.forbiddenTools().stream().noneMatch(called::contains);
        boolean numbersOk = r.unverifiedNumbers().isEmpty();
        List<String> missing = e.mustContain().stream().filter(p -> !r.answer().contains(p)).toList();
        List<String> forbidden = e.mustNotContain().stream().filter(r.answer()::contains).toList();
        return new RuleChecks(toolsOk, numbersOk, missing, forbidden, routeOk);
    }

    /**
     * Null without an expectation. {@code none} needs a real router decision of "no category"; a key must match
     * exactly, and a fallback never counts. A failed turn fails the check.
     */
    private static Boolean routeCheck(String expected, EvaluationReport r) {
        if (expected == null) {
            return null;
        }
        if (!"ANSWERED".equals(r.outcome()) || "FALLBACK".equals(r.routeSource())) {
            return false;
        }
        if ("none".equals(expected)) {
            return r.instructionKey() == null && "ROUTER".equals(r.routeSource());
        }
        return expected.equals(r.instructionKey());
    }
}
