package com.example.copilot.aiassistant.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LlmConfigurationTest {

    @Test
    void nullTemperatureIsNotSent() {
        assertThat(model(null, null).defaultRequestParameters().temperature()).isNull();
    }

    @Test
    void configuredTemperatureIsSent() {
        assertThat(model(0.2, null).defaultRequestParameters().temperature()).isEqualTo(0.2);
    }

    @Test
    void nullReasoningEffortIsNotSent() {
        assertThat(parameters(model(null, null)).reasoningEffort()).isNull();
    }

    @Test
    void configuredReasoningEffortIsSent() {
        assertThat(parameters(model(null, "none")).reasoningEffort()).isEqualTo("none");
    }

    private static OpenAiChatRequestParameters parameters(ChatModel model) {
        return (OpenAiChatRequestParameters) model.defaultRequestParameters();
    }

    private static ChatModel model(Double temperature, String reasoningEffort) {
        AiAssistantProperties.Llm llm = new AiAssistantProperties.Llm("openai", "test-key", "test-model",
                temperature, reasoningEffort, Duration.ofSeconds(30), 1500);
        return new LlmConfiguration().chatModel(new AiAssistantProperties(llm, null, null, null, null, null));
    }
}
