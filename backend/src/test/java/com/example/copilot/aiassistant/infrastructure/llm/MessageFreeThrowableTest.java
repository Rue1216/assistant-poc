package com.example.copilot.aiassistant.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class MessageFreeThrowableTest {

    @Test
    void keepsTypesAndFramesDropsMessages() {
        RateLimitException source = new RateLimitException(new HttpException(429, "SECRET-BODY"));
        Throwable copy = MessageFreeThrowable.of(source);

        StringWriter out = new StringWriter();
        copy.printStackTrace(new PrintWriter(out));
        assertThat(out.toString())
                .doesNotContain("SECRET-BODY")
                .startsWith("dev.langchain4j.exception.RateLimitException")
                .contains("Caused by: dev.langchain4j.exception.HttpException");
        assertThat(copy.getMessage()).isNull();
        assertThat(copy.getCause().getMessage()).isNull();
        assertThat(copy.getStackTrace()).isEqualTo(source.getStackTrace());
        assertThat(copy.getCause().getStackTrace()).isEqualTo(source.getCause().getStackTrace());
    }

    @Test
    void cyclicCauseChainTerminates() {
        IllegalStateException a = new IllegalStateException("a");
        IllegalArgumentException b = new IllegalArgumentException("b", a);
        a.initCause(b);

        Throwable copy = MessageFreeThrowable.of(a);

        assertThat(copy.toString()).isEqualTo("java.lang.IllegalStateException");
        assertThat(copy.getCause().toString()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(copy.getCause().getCause()).isNull();
    }

    @Test
    void nullGivesNull() {
        assertThat(MessageFreeThrowable.of(null)).isNull();
    }
}
