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
    }
}
