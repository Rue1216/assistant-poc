package com.example.copilot.aiassistant.evaluation;

import com.example.copilot.aiassistant.chat.EvaluationReport;
import java.util.List;

/** One side (baseline or candidate) of one evaluated case (prompt-evaluation spec 5.2). */
public record CaseResult(String outcome, String errorCode, String answer, List<EvaluationReport.ToolCall> toolCalls,
                         int modelCalls, int inputTokens, int outputTokens, int totalTokens, int durationMs,
                         List<String> unverifiedNumbers, boolean toolsOk, boolean numbersOk,
                         List<String> missingPhrases, List<String> forbiddenPhrases, JudgeVerdict judge,
                         boolean passed) {

    static final int MAX_ANSWER = 8000;
    static final int MAX_TOOL_CALLS = 10;
    static final int MAX_UNVERIFIED = 20;
    static final int MAX_UNVERIFIED_LENGTH = 32;
    static final int MAX_TOOL_NAME = 64;

    public static CaseResult of(EvaluationReport r, RuleChecks checks, JudgeVerdict judge, boolean expectRefusal) {
        boolean passed = "ANSWERED".equals(r.outcome())
                && checks.toolsOk() && checks.numbersOk()
                && checks.missingPhrases().isEmpty() && checks.forbiddenPhrases().isEmpty()
                && judge != null && judge.error() == null
                && judge.refused() != null && judge.refused() == expectRefusal
                && judge.rubric().stream().allMatch(JudgeVerdict.RubricResult::met);
        return new CaseResult(r.outcome(), r.errorCode(), clip(r.answer()),
                first(r.toolCalls(), MAX_TOOL_CALLS).stream()
                        .map(t -> new EvaluationReport.ToolCall(clip(t.toolName(), MAX_TOOL_NAME), t.status()))
                        .toList(), r.modelCalls(), toInt(r.inputTokens()),
                toInt(r.outputTokens()), toInt(r.totalTokens()), toInt(r.durationMs()),
                first(r.unverifiedNumbers(), MAX_UNVERIFIED).stream().map(n -> clip(n, MAX_UNVERIFIED_LENGTH))
                        .toList(), checks.toolsOk(), checks.numbersOk(),
                checks.missingPhrases(), checks.forbiddenPhrases(), judge, passed);
    }

    /** Clips by code points so a surrogate pair is never split. */
    private static String clip(String s) {
        return clip(s, MAX_ANSWER);
    }

    private static String clip(String s, int max) {
        if (s == null || s.codePointCount(0, s.length()) <= max) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, max));
    }

    private static <T> List<T> first(List<T> list, int max) {
        return list.size() <= max ? list : List.copyOf(list.subList(0, max));
    }

    private static int toInt(long v) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, v));
    }
}
