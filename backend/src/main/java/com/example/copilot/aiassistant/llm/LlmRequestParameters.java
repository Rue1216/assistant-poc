package com.example.copilot.aiassistant.llm;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;

/**
 * Per-request parameters for calls that differ from the {@link LlmConfiguration} defaults. Keeps the provider type
 * inside this package (01 section 6); unset fields fall back to the model's defaults.
 */
public final class LlmRequestParameters {

    private LlmRequestParameters() {
    }

    /** @param reasoningEffort null or blank keeps {@code ai-assistant.llm.reasoning-effort} */
    public static ChatRequestParameters of(ResponseFormat responseFormat, String reasoningEffort) {
        if (reasoningEffort == null || reasoningEffort.isBlank()) {
            return DefaultChatRequestParameters.builder().responseFormat(responseFormat).build();
        }
        return OpenAiChatRequestParameters.builder().responseFormat(responseFormat)
                .reasoningEffort(reasoningEffort.strip()).build();
    }
}
