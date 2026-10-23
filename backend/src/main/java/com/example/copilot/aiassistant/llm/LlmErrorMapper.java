package com.example.copilot.aiassistant.llm;

import com.example.copilot.aiassistant.common.ErrorCode;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.RateLimitException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Classifies a chat model failure by walking its cause chain (05 section 2.2). */
@Component
public class LlmErrorMapper {

    public ErrorCode map(Throwable t) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = t; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof dev.langchain4j.exception.TimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return ErrorCode.AI_LLM_TIMEOUT;
            }
            // OpenAI refusals arrive as a content filter exception.
            if (current instanceof ContentFilteredException) {
                return ErrorCode.AI_ANSWER_FORMAT_INVALID;
            }
            if (current instanceof RateLimitException) {
                return ErrorCode.AI_LLM_RATE_LIMITED;
            }
        }
        return ErrorCode.AI_LLM_UNAVAILABLE;
    }
}
