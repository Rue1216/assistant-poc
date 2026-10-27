package com.example.copilot.aiassistant.evaluation;

import java.util.List;

/** Aggregated numbers of one side of a run (prompt-evaluation spec 4.6). Averages are integer divisions. */
public record EvalMetrics(int correct, int total, int toolsOk, int refusedExpected, int expectedRefusals,
                          int refusedNormal, int normalCases, int routeOk, int routeCases, long avgTotalTokens, long avgInputTokens,
                          long avgOutputTokens, long avgDurationMs) {

    public static EvalMetrics of(List<EvalCaseRecord> cases, boolean candidate) {
        int total = 0;
        int correct = 0;
        int toolsOk = 0;
        int refusedExpected = 0;
        int expectedRefusals = 0;
        int refusedNormal = 0;
        int normalCases = 0;
        int routeOk = 0;
        int routeCases = 0;
        long totalTokens = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        long durationMs = 0;
        for (EvalCaseRecord c : cases) {
            CaseResult r = candidate ? c.candidate() : c.baseline();
            if (r == null) {
                continue;
            }
            total++;
            if (r.passed()) {
                correct++;
            }
            if (r.toolsOk()) {
                toolsOk++;
            }
            // A failed judgement (refused == null) is neither a refusal nor an answer.
            boolean refused = r.judge() != null && Boolean.TRUE.equals(r.judge().refused());
            if (c.expectRefusal()) {
                expectedRefusals++;
                if (refused) {
                    refusedExpected++;
                }
            } else {
                normalCases++;
                if (refused) {
                    refusedNormal++;
                }
            }
            if (c.expectCategory() != null) {
                routeCases++;
            }
            if (Boolean.TRUE.equals(r.routeOk())) {
                routeOk++;
            }
            totalTokens += r.totalTokens();
            inputTokens += r.inputTokens();
            outputTokens += r.outputTokens();
            durationMs += r.durationMs();
        }
        long n = Math.max(total, 1);
        return new EvalMetrics(correct, total, toolsOk, refusedExpected, expectedRefusals, refusedNormal,
                normalCases, routeOk, routeCases, totalTokens / n, inputTokens / n, outputTokens / n, durationMs / n);
    }
}
