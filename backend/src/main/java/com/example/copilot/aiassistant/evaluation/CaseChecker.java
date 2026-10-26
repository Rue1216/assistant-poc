package com.example.copilot.aiassistant.evaluation;

import com.example.copilot.aiassistant.chat.EvaluationReport;
import java.util.List;

/** Pure rule checks of one answer against the case expectations (prompt-evaluation spec 4.4). */
public final class CaseChecker {

    private CaseChecker() {
    }

    public static RuleChecks check(EvalCase c, EvaluationReport r) {
        EvalCase.Expect e = c.expect();
        if (!"ANSWERED".equals(r.outcome()) || r.answer() == null) {
            // A turn without an answer fails every check; nothing in it can be forbidden.
            return new RuleChecks(false, false, e.mustContain(), List.of());
        }
        // Any requested call counts, including denied ones and calls over the per-question limit.
        List<String> called = r.toolCalls().stream().map(EvaluationReport.ToolCall::toolName).toList();
        boolean toolsOk = called.containsAll(e.tools()) && e.forbiddenTools().stream().noneMatch(called::contains);
        boolean numbersOk = r.unverifiedNumbers().isEmpty();
        List<String> missing = e.mustContain().stream().filter(p -> !r.answer().contains(p)).toList();
        List<String> forbidden = e.mustNotContain().stream().filter(r.answer()::contains).toList();
        return new RuleChecks(toolsOk, numbersOk, missing, forbidden);
    }
}
