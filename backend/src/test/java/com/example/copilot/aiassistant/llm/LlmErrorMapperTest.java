package com.example.copilot.aiassistant.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.common.ErrorCode;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class LlmErrorMapperTest {

    private final LlmErrorMapper mapper = new LlmErrorMapper();

    @Test
    void timeoutsAnywhereInTheCauseChainMapToLlmTimeout() {
        assertThat(mapper.map(new TimeoutException("timed out"))).isEqualTo(ErrorCode.AI_LLM_TIMEOUT);
        assertThat(mapper.map(new RuntimeException(new HttpTimeoutException("request timed out"))))
                .isEqualTo(ErrorCode.AI_LLM_TIMEOUT);
        assertThat(mapper.map(new CompletionException(new RuntimeException(new SocketTimeoutException("read")))))
                .isEqualTo(ErrorCode.AI_LLM_TIMEOUT);
    }

    @Test
    void rateLimitMapsToLlmRateLimited() {
        assertThat(mapper.map(new RateLimitException("429"))).isEqualTo(ErrorCode.AI_LLM_RATE_LIMITED);
        assertThat(mapper.map(new RuntimeException(new RateLimitException("429"))))
                .isEqualTo(ErrorCode.AI_LLM_RATE_LIMITED);
    }

    @Test
    void everythingElseMapsToLlmUnavailable() {
        assertThat(mapper.map(new AuthenticationException("401"))).isEqualTo(ErrorCode.AI_LLM_UNAVAILABLE);
        assertThat(mapper.map(new InternalServerException("500"))).isEqualTo(ErrorCode.AI_LLM_UNAVAILABLE);
        assertThat(mapper.map(new HttpException(502, "bad gateway"))).isEqualTo(ErrorCode.AI_LLM_UNAVAILABLE);
        assertThat(mapper.map(new IllegalStateException("boom"))).isEqualTo(ErrorCode.AI_LLM_UNAVAILABLE);
    }
}
