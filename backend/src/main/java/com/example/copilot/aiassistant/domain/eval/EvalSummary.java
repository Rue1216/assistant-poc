package com.example.copilot.aiassistant.domain.eval;

/** Run-level metrics of both sides plus the tokens the judge used. */
public record EvalSummary(EvalMetrics baseline, EvalMetrics candidate, long judgeTokens) {
}
