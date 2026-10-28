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

class EvalRunnerUnknownCustomerTest extends EvalRunnerTestBase {

    @TestConfiguration
    static class WithUnknownCustomer {
        @Bean
        @Primary
        EvalDataset unknownCustomerDataset() {
            return new EvalDataset(List.of(
                    evalCase("c1", TestCustomers.RETIREE, AccessLevel.FULL, false),
                    evalCase("c2", TestCustomers.UNKNOWN, AccessLevel.FULL, false),
                    evalCase("c3", TestCustomers.RETIREE, AccessLevel.FULL, false)), "d".repeat(64));
        }
    }

    @Test
    void unknownCustomerFailsCase() {
        scriptFullCase();
        scriptFullCase();
        EvalRun run = insertRun(3, Duration.ofSeconds(1));

        runner.run(run, snapshot());

        EvalRun done = runs.findById(run.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(EvalStatus.COMPLETED);
        EvalCaseRecord unknown = done.cases().get(1);
        // Neither side reached the model, so the dataset question (a fixed test-customer string) is stored.
        assertThat(unknown.question()).isEqualTo("question of c2");
        for (CaseResult side : List.of(unknown.baseline(), unknown.candidate())) {
            assertThat(side.outcome()).isEqualTo("FAILED");
            assertThat(side.errorCode()).isEqualTo("AI_CUSTOMER_NOT_FOUND");
            assertThat(side.judge()).isNull();
            assertThat(side.passed()).isFalse();
        }
        assertThat(done.cases().get(0).baseline().outcome()).isEqualTo("ANSWERED");
        assertThat(done.cases().get(2).candidate().outcome()).isEqualTo("ANSWERED");
        assertThat(scripted.requests()).hasSize(8);
    }
}
