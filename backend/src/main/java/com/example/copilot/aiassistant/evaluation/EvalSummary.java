package com.example.copilot.aiassistant.evaluation;

/** Run-level metrics of both sides plus the tokens the judge used. */
public record EvalSummary(EvalMetrics baseline, EvalMetrics candidate, long judgeTokens) {
}
