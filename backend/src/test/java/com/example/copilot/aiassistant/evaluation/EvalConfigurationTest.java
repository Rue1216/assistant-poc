package com.example.copilot.aiassistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.tool.ToolRegistry;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class EvalConfigurationTest {

    private static final Set<String> TOOLS = Set.of("getCustomer", "getRealTimeCustomer",
            "getMarketIndex", "getStockQuote",
            "getCompanyAnnouncements");

    @Test
    void loadsBundledDatasetWithinStoredLimit() {
        AiAssistantProperties props = new AiAssistantProperties(null, null, null, null, null,
                new AiAssistantProperties.Eval("classpath:eval/eval-cases.yml", Duration.ofMinutes(30),
                        Duration.ofMinutes(10)));
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.names()).thenReturn(TOOLS);

        EvalDataset dataset = new EvalConfiguration().evalDataset(props, new DefaultResourceLoader(), registry);

        assertThat(dataset.cases()).isNotEmpty().hasSizeLessThanOrEqualTo(EvalConfiguration.STORED_CASES_MAX);
    }
}
