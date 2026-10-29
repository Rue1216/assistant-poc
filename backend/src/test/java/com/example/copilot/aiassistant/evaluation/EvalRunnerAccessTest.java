package com.example.copilot.aiassistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.port.AccessLevel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

class EvalRunnerAccessTest extends EvalRunnerTestBase {

    @TestConfiguration
    static class WithStopUseCustomer {
        @Bean
        @Primary
        EvalDataset stopUseDataset() {
            return new EvalDataset(List.of(
                    evalCase("c1", TestCustomers.RETIREE, AccessLevel.FULL, false),
                    evalCase("c2", TestCustomers.STOP_USE, AccessLevel.FULL, false)), "d".repeat(64));
        }
    }

    @Test
    void customerTheTriggeringUserCannotAccessIsNotSentToTheModel() {
        scriptFullCase();
        EvalRun run = insertRun(2, Duration.ofSeconds(1));

        runner.run(run, snapshot());

        EvalRun done = runs.findById(run.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(EvalStatus.COMPLETED);
        EvalCaseRecord denied = done.cases().get(1);
        assertThat(denied.question()).isEqualTo("question of c2");
        for (CaseResult side : List.of(denied.baseline(), denied.candidate())) {
            assertThat(side.outcome()).isEqualTo("FAILED");
            assertThat(side.errorCode()).isEqualTo("AI_CUSTOMER_NOT_FOUND");
            assertThat(side.judge()).isNull();
        }
        assertThat(done.cases().get(0).baseline().outcome()).isEqualTo("ANSWERED");
        assertThat(scripted.requests()).hasSize(4);
    }
}
