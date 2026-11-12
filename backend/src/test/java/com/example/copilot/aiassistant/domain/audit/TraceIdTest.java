package com.example.copilot.aiassistant.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import org.junit.jupiter.api.Test;

class TraceIdTest {

    private static void assertInvalid(Runnable action, String field) {
        AssistantException e = catchThrowableOfType(AssistantException.class, action::run);
        assertThat(e).isNotNull();
        assertThat(e.code()).isEqualTo(ErrorCode.AI_INPUT_INVALID);
        assertThat(e.details()).containsExactly(new FieldDetail(field));
    }

    @Test void rejectsNull() { assertInvalid(() -> new TraceId(null), "traceId"); }

    @Test void acceptsSixteenAndThirtyTwo() {
        assertThat(new TraceId("0123456789abcdef").value()).hasSize(16);
        assertThat(new TraceId("0123456789abcdef0123456789abcdef").value()).hasSize(32);
    }
    @Test void rejectsFifteen() { assertInvalid(() -> new TraceId("0123456789abcde"), "traceId"); }
    @Test void rejectsThirtyThree() { assertInvalid(() -> new TraceId("0123456789abcdef0123456789abcdef0"), "traceId"); }
    @Test void rejectsUppercase() { assertInvalid(() -> new TraceId("0123456789ABCDEF"), "traceId"); }
    @Test void toStringIsValue() { assertThat(new TraceId("0123456789abcdef").toString()).isEqualTo("0123456789abcdef"); }
}
