package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.domain.chat.EvaluationReport;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import com.example.copilot.aiassistant.domain.eval.CaseResult;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalMetrics;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import com.example.copilot.aiassistant.domain.eval.EvalSummary;
import com.example.copilot.aiassistant.domain.eval.JudgeVerdict;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class EvalRunDocumentMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-20T01:00:00Z");

    private final EvalRunDocumentMapper mapper = Mappers.getMapper(EvalRunDocumentMapper.class);

    @Test
    void roundTrip() {
        JudgeVerdict judge = new JudgeVerdict(false, List.of(new JudgeVerdict.RubricResult("r", true)), "ok", null, 7);
        CaseResult withJudge = new CaseResult("ANSWERED", null, "a",
                List.of(new EvaluationReport.ToolCall("tool", "EXECUTED")), 1, 2, 3, 5, 9, List.of("1"), true, true,
                List.of("m"), List.of("f"), judge, true, true, "ROUTER", "instruction.x");
        CaseResult failed = new CaseResult("FAILED", "E1", null, List.of(), 0, 0, 0, 0, 0, List.of(), false, false,
                List.of(), List.of(), JudgeVerdict.failed(3), false, null, null, null);
        EvalMetrics m = new EvalMetrics(1, 2, 1, 0, 0, 0, 2, 1, 1, 5, 2, 3, 9);
        EvalRun run = new EvalRun("665f1f77bcf86cd799439011", new ConfigKey("instruction.x"), 2, 3L, NOW,
                List.of(new PromptVersion("instruction.x", 1)), "a".repeat(64), EvalStatus.COMPLETED, null, "tester",
                NOW, NOW, NOW, 2, 2, new EvalSummary(m, m, 14L),
                List.of(new EvalCaseRecord("c1", "g", "q", false, "cat", withJudge, withJudge),
                        new EvalCaseRecord("c2", "g", "q2", true, null, failed, withJudge)),
                4L);

        EvalRunDocument doc = mapper.toDocument(run);

        assertThat(doc.getId()).isEqualTo("665f1f77bcf86cd799439011");
        assertThat(doc.getConfigKey()).isEqualTo("instruction.x");
        assertThat(doc.getCases()).hasSize(2);
        assertThat(mapper.toDomain(doc)).isEqualTo(run);
    }

    @Test
    void mapsCaseAndSummary() {
        EvalMetrics m = new EvalMetrics(1, 2, 1, 0, 0, 0, 2, 1, 1, 5, 2, 3, 9);

        assertThat(mapper.toDocument(new EvalSummary(m, m, 14L)).getJudgeTokens()).isEqualTo(14L);
        assertThat(mapper.toDocument(new EvalSummary(m, m, 14L)).getBaseline().getRouteCases()).isEqualTo(1);
    }
}
