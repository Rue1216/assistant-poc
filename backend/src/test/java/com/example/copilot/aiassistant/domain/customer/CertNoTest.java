package com.example.copilot.aiassistant.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import org.junit.jupiter.api.Test;

class CertNoTest {

    private static void assertInvalid(Runnable action, String field) {
        AssistantException e = catchThrowableOfType(AssistantException.class, action::run);
        assertThat(e).isNotNull();
        assertThat(e.code()).isEqualTo(ErrorCode.AI_INPUT_INVALID);
        assertThat(e.details()).containsExactly(new FieldDetail(field));
    }

    @Test void rejectsNull() { assertInvalid(() -> new CertNo(null), "certNo"); }

    @Test void acceptsValid() { assertThat(new CertNo("A123456789").value()).isEqualTo("A123456789"); }
    @Test void rejectsLowercase() { assertInvalid(() -> new CertNo("a123456789"), "certNo"); }
    @Test void rejectsTooShort() { assertInvalid(() -> new CertNo("A12"), "certNo"); }
    @Test void toStringHidesValue() { assertThat(new CertNo("A123456789").toString()).doesNotContain("A123456789").isEqualTo("CertNo[***]"); }
}
