package com.example.copilot.aiassistant.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.application.llm.LlmErrorMapper;
import com.example.copilot.aiassistant.application.llm.LlmRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

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
    void blankReasoningEffortIsNotSent() {
        assertThat(parameters(model(null, " ")).reasoningEffort()).isNull();
    }

    @Test
    void configuredReasoningEffortIsSent() {
        assertThat(parameters(model(null, "none")).reasoningEffort()).isEqualTo("none");
    }

    @Test
    void blankBaseUrlUsesTheDefaultEndpoint() {
        assertThat(model(null, null, " ")).isNotNull();
        assertThat(model(null, null, "https://gateway.example/v1")).isNotNull();
    }

    @Test
    void requestReasoningEffortOverridesTheDefaultOnly() {
        ChatModel model = model(null, "none");

        ChatRequestParameters merged = model.defaultRequestParameters()
                .overrideWith(LlmRequestParameters.of(ResponseFormat.JSON, "medium"));

        assertThat(((OpenAiChatRequestParameters) merged).reasoningEffort()).isEqualTo("medium");
        assertThat(merged.modelName()).isEqualTo("test-model");
        assertThat(((OpenAiChatRequestParameters) merged).maxCompletionTokens()).isEqualTo(1500);
        assertThat(merged.responseFormat()).isEqualTo(ResponseFormat.JSON);
    }

    @Test
    void blankRequestReasoningEffortKeepsTheDefault() {
        ChatModel model = model(null, "none");

        ChatRequestParameters merged = model.defaultRequestParameters()
                .overrideWith(LlmRequestParameters.of(ResponseFormat.JSON, ""));

        assertThat(((OpenAiChatRequestParameters) merged).reasoningEffort()).isEqualTo("none");
        assertThat(merged.responseFormat()).isEqualTo(ResponseFormat.JSON);
    }

    @Test
    void registersTheOutboundLogListener() {
        OutboundLlmLogListener listener = listener();
        ChatModel model = new LlmConfiguration().chatModel(props(null, null, null), listener);
        assertThat(model.listeners()).containsExactly(listener);
    }

    private static OpenAiChatRequestParameters parameters(ChatModel model) {
        return (OpenAiChatRequestParameters) model.defaultRequestParameters();
    }

    private static ChatModel model(Double temperature, String reasoningEffort) {
        return model(temperature, reasoningEffort, null);
    }

    private static ChatModel model(Double temperature, String reasoningEffort, String baseUrl) {
        return new LlmConfiguration().chatModel(props(temperature, reasoningEffort, baseUrl), listener());
    }

    private static AiAssistantProperties props(Double temperature, String reasoningEffort, String baseUrl) {
        AiAssistantProperties.Llm llm = new AiAssistantProperties.Llm("openai", "test-key", baseUrl, "test-model",
                temperature, reasoningEffort, null, null, Duration.ofSeconds(30), 1500);
        return new AiAssistantProperties(llm, null, null, null, null, null);
    }

    private static OutboundLlmLogListener listener() {
        return new OutboundLlmLogListener(new LlmErrorMapper(), JsonMapper.builder().build());
    }
}
