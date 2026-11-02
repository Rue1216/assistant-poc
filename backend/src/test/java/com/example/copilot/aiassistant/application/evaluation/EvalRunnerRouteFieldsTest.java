package com.example.copilot.aiassistant.application.evaluation;

import static com.example.copilot.aiassistant.application.chat.ScriptedChatModel.text;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.eval.EvalCase;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalDataset;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** The route fields written by {@link EvalRunner} survive the repository round trip. */
class EvalRunnerRouteFieldsTest extends EvalRunnerTestBase {

    private static final String KEY = "instruction.contact-prep";

    @TestConfiguration
    static class OneRoutedCase {
        @Bean
        @Primary
        EvalDataset routedDataset() {
            return new EvalDataset(List.of(
                    new EvalCase("r1", "g", AccessLevel.FULL, TestCustomers.RETIREE, "question of r1",
                            new EvalCase.Expect(List.of(), List.of(), false, List.of(), List.of(), KEY),
                            List.of("rubric of r1"))), "d".repeat(64));
        }
    }

    @Test
    void routeFieldsAreStoredAndReadBack() {
        // baseline routes as expected, candidate routes to none
        scripted.reply(text("{\"category\":\"" + KEY + "\"}", 10));
        answerStep();
        judgeStep();
        routeStep();
        answerStep();
        judgeStep();
        EvalRun run = insertRun(1, Duration.ofSeconds(1));

        runner.run(run, snapshot(
                List.of(config(KEY, 1, "## 適用問題\nx\n\n## 回答方式\nx")),
                List.of(config(KEY, 2, "## 適用問題\nx\n\n## 回答方式\nx"))));

        EvalRun done = runs.findById(run.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(EvalStatus.COMPLETED);
        EvalCaseRecord c = done.cases().get(0);
        assertThat(c.expectCategory()).isEqualTo(KEY);
        assertThat(c.baseline().routeOk()).isTrue();
        assertThat(c.baseline().routeSource()).isEqualTo("ROUTER");
        assertThat(c.baseline().instructionKey()).isEqualTo(KEY);
        assertThat(c.candidate().routeOk()).isFalse();
        assertThat(c.candidate().routeSource()).isEqualTo("ROUTER");
        assertThat(c.candidate().instructionKey()).isNull();
        assertThat(done.summary().baseline().routeOk()).isEqualTo(1);
        assertThat(done.summary().baseline().routeCases()).isEqualTo(1);
        assertThat(done.summary().candidate().routeOk()).isZero();
        assertThat(done.summary().candidate().routeCases()).isEqualTo(1);
    }
}
