package com.example.copilot.aiassistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.tool.ToolRegistry;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class EvalConfigurationTest {

    private static final Set<String> TOOLS = Set.of("getCustomerProfile", "getStockQuote", "getCompanyAnnouncements",
            "getMarketIndex");

    private static EvalDataset load(int maxCases) {
        AiAssistantProperties props = new AiAssistantProperties(null, null, null, null, null,
                new AiAssistantProperties.Eval("classpath:eval/eval-cases.yml", maxCases, Duration.ofMinutes(30),
                        Duration.ofMinutes(10)));
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.names()).thenReturn(TOOLS);
        return new EvalConfiguration().evalDataset(props, new DefaultResourceLoader(), registry);
    }

    @Test
    void maxCasesWithinStoredLimitLoads() {
        assertThat(load(30).cases()).hasSize(10);
        assertThat(load(10).cases()).hasSize(10);
    }

    @Test
    void maxCasesOutsideStoredLimitFailsStart() {
        // The run validator caps cases at 30 items; a larger setting would let runs fail to store.
        for (int maxCases : new int[] {0, -1, 31}) {
            assertThatThrownBy(() -> load(maxCases)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("ai-assistant.eval.max-cases 必須介於 1 與 30 之間，目前為 " + maxCases);
        }
    }
}
