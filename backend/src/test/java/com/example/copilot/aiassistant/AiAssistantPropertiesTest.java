package com.example.copilot.aiassistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AiAssistantPropertiesTest extends IntegrationTestBase {

    @Autowired
    AiAssistantProperties props;

    @Test
    void bindsDefaultsFromYaml() {
        assertThat(props.limits().maxToolCalls()).isEqualTo(5);
        assertThat(props.limits().requestTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(props.market().twse().maxResponseBytes()).isEqualTo(2_097_152);
        assertThat(props.config().scheduleMaxLead()).isEqualTo(Duration.ofDays(90));
        assertThat(props.config().allowedUrlHosts()).isEmpty();
    }

    @Test
    void auditStoreToolResultsDefaultsToFalse() {
        assertThat(props.audit().storeToolResults()).isFalse();
    }

    @Test
    void llmToStringMasksApiKey() {
        AiAssistantProperties.Llm llm = new AiAssistantProperties.Llm("openai", "sk-secret-value-1234567890",
                "model-x", 0.2, Duration.ofSeconds(30), 800);

        assertThat(llm.toString()).doesNotContain("sk-secret-value-1234567890").contains("apiKey=***")
                .contains("model-x");
    }
}
