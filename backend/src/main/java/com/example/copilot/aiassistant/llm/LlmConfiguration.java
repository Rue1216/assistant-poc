package com.example.copilot.aiassistant.llm;

import com.example.copilot.aiassistant.AiAssistantProperties;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The only place that knows the provider; everything else depends on {@link ChatModel} (01 section 6). */
@Configuration(proxyBeanMethods = false)
public class LlmConfiguration {

    @Bean
    ChatModel chatModel(AiAssistantProperties props) {
        AiAssistantProperties.Llm llm = props.llm();
        return OpenAiChatModel.builder()
                .apiKey(llm.apiKey())
                .modelName(llm.modelName())
                .temperature(llm.temperature())
                .reasoningEffort(llm.reasoningEffort())
                .timeout(llm.timeout())
                .maxCompletionTokens(llm.maxOutputTokens())
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA) // answers are JSON blocks (answer-citations 2.2)
                .strictJsonSchema(true)
                .maxRetries(0) // timeouts and rate limits are reported to the user, not retried here
                .logRequests(false) // request/response bodies hold prompts and tool results
                .logResponses(false)
                .build();
    }
}
