package com.example.copilot.aiassistant.application.llm;

import dev.langchain4j.model.chat.ChatRequestOptions;
import java.util.Map;
import java.util.Optional;

/** Why a model call is made; travels as a listener attribute so the Outbound log can say which step it was. */
public enum LlmCallPurpose {
    ROUTE, SUMMARY, STEP_ARGUMENTS, ANSWER, JUDGE;

    public ChatRequestOptions options() {
        return ChatRequestOptions.builder().addListenerAttribute(LlmCallPurpose.class, this).build();
    }

    public static Optional<LlmCallPurpose> from(Map<Object, Object> attributes) {
        if (attributes != null && attributes.get(LlmCallPurpose.class) instanceof LlmCallPurpose purpose) {
            return Optional.of(purpose);
        }
        return Optional.empty();
    }
}
