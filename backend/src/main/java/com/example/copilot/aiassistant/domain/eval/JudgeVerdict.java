package com.example.copilot.aiassistant.domain.eval;

import java.util.List;

/** The judge model's verdict for one answer; {@code refused} is null and {@code error} set when judging failed. */
public record JudgeVerdict(Boolean refused, List<RubricResult> rubric, String comment, String error, int tokens) {

    public record RubricResult(String text, boolean met) {
    }

    public static JudgeVerdict failed(int tokens) {
        return new JudgeVerdict(null, List.of(), null, EvalErrors.JUDGE_FAILED, tokens);
    }
}
