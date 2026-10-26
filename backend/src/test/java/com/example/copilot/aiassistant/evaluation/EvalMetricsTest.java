package com.example.copilot.aiassistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.chat.EvaluationReport;
import com.example.copilot.aiassistant.evaluation.JudgeVerdict.RubricResult;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EvalMetricsTest {

    private static final RuleChecks OK = new RuleChecks(true, true, List.of(), List.of());

    private static EvaluationReport answered(long total) {
        return new EvaluationReport("ANSWERED", null, "q", "a", List.of(), 1, total / 2, total / 2, total, 100,
                List.of());
    }

    private static JudgeVerdict verdict(Boolean refused, boolean met) {
        return new JudgeVerdict(refused, List.of(new RubricResult("r", met)), "c", null, 5);
    }

    private static RuleChecks checks(boolean tools, boolean numbers, List<String> missing, List<String> forbidden) {
        return new RuleChecks(tools, numbers, missing, forbidden);
    }

    @Test
    void passedNeedsEverything() {
        assertThat(CaseResult.of(answered(10), OK, verdict(false, true), false).passed()).isTrue();
        assertThat(CaseResult.of(answered(10), checks(false, true, List.of(), List.of()), verdict(false, true),
                false).passed()).isFalse();
        assertThat(CaseResult.of(answered(10), checks(true, false, List.of(), List.of()), verdict(false, true),
                false).passed()).isFalse();
        assertThat(CaseResult.of(answered(10), checks(true, true, List.of("x"), List.of()), verdict(false, true),
                false).passed()).isFalse();
        assertThat(CaseResult.of(answered(10), checks(true, true, List.of(), List.of("x")), verdict(false, true),
                false).passed()).isFalse();
        assertThat(CaseResult.of(answered(10), OK, verdict(true, true), false).passed()).isFalse();
        assertThat(CaseResult.of(answered(10), OK, verdict(false, false), false).passed()).isFalse();
        assertThat(CaseResult.of(answered(10), OK, JudgeVerdict.failed(3), false).passed()).isFalse();
        assertThat(CaseResult.of(answered(10), OK, null, false).passed()).isFalse();
        assertThat(CaseResult.of(EvaluationReport.failed("X"), OK, verdict(false, true), false).passed()).isFalse();
        assertThat(CaseResult.of(answered(10), OK, verdict(true, true), true).passed()).isTrue();
    }

    @Test
    void ofClipsAndClamps() {
        String longAnswer = "😀".repeat(8001);
        List<String> nums = IntStream.range(0, 25).mapToObj(String::valueOf).toList();
        List<EvaluationReport.ToolCall> calls = IntStream.range(0, 12)
                .mapToObj(i -> new EvaluationReport.ToolCall("t" + i, "EXECUTED")).toList();
        List<EvaluationReport.ToolCall> longName = List.of(new EvaluationReport.ToolCall("😀".repeat(70), "EXECUTED"));
        EvaluationReport r = new EvaluationReport("ANSWERED", null, "q", longAnswer, calls, 1,
                Long.MAX_VALUE, -5, 3_000_000_000L, 7, nums);
        CaseResult res = CaseResult.of(r, OK, verdict(false, true), false);
        EvaluationReport wide = new EvaluationReport("ANSWERED", null, "q", "a", longName, 1, 1, 1, 2, 7,
                List.of("9".repeat(100), "12"));
        CaseResult clipped = CaseResult.of(wide, OK, verdict(false, true), false);
        assertThat(clipped.toolCalls().get(0).toolName().codePointCount(0, clipped.toolCalls().get(0).toolName().length()))
                .isEqualTo(64);
        assertThat(clipped.unverifiedNumbers()).containsExactly("9".repeat(32), "12");
        assertThat(res.answer().codePointCount(0, res.answer().length())).isEqualTo(8000);
        assertThat(res.toolCalls()).hasSize(10);
        assertThat(res.unverifiedNumbers()).hasSize(20);
        assertThat(res.inputTokens()).isEqualTo(Integer.MAX_VALUE);
        assertThat(res.outputTokens()).isZero();
        assertThat(res.totalTokens()).isEqualTo(Integer.MAX_VALUE);
    }

    private static EvalCaseRecord rec(String id, boolean expectRefusal, CaseResult cand) {
        return new EvalCaseRecord(id, "g", "q", expectRefusal, null, cand);
    }

    @Test
    void countsAndAverages() {
        EvaluationReport failed900 = new EvaluationReport("FAILED", "AI_LLM_TIMEOUT", null, null, List.of(), 1,
                400, 500, 900, 100, List.of());
        List<EvalCaseRecord> cases = List.of(
                rec("1", true, CaseResult.of(answered(100), OK, verdict(true, true), true)),
                rec("2", true, CaseResult.of(answered(200), OK, verdict(false, true), true)),
                rec("3", false, CaseResult.of(answered(301), OK, verdict(true, true), false)),
                rec("4", false, CaseResult.of(failed900, OK, null, false)));
        EvalMetrics m = EvalMetrics.of(cases, true);
        assertThat(m.total()).isEqualTo(4);
        assertThat(m.correct()).isEqualTo(1);
        assertThat(m.toolsOk()).isEqualTo(4);
        assertThat(m.refusedExpected()).isEqualTo(1);
        assertThat(m.expectedRefusals()).isEqualTo(2);
        assertThat(m.refusedNormal()).isEqualTo(1);
        assertThat(m.normalCases()).isEqualTo(2);
        assertThat(m.avgTotalTokens()).isEqualTo((100 + 200 + 301 + 900) / 4);
        assertThat(m.avgDurationMs()).isEqualTo(100);
        assertThat(EvalMetrics.of(cases, false).total()).isZero();
        assertThat(EvalMetrics.of(List.of(), true).avgTotalTokens()).isZero();
    }

    @Test
    void judgeFailureIsNotRefusal() {
        JudgeVerdict nullRefused = new JudgeVerdict(null, List.of(), null, EvalErrors.JUDGE_FAILED, 0);
        List<EvalCaseRecord> cases = List.of(
                rec("1", true, CaseResult.of(answered(10), OK, nullRefused, true)),
                rec("2", false, CaseResult.of(answered(10), OK, nullRefused, false)));
        EvalMetrics m = EvalMetrics.of(cases, true);
        assertThat(m.refusedExpected()).isZero();
        assertThat(m.refusedNormal()).isZero();
    }
}
