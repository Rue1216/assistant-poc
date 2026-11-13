package com.example.copilot.aiassistant.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScheduleWindowTest {

    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
    private final ScheduleWindow window = new ScheduleWindow(Duration.ofMinutes(1), Duration.ofDays(30));

    private void assertRejected(Instant at) {
        AssistantException e = catchThrowableOfType(AssistantException.class, () -> window.requireWithin(at, NOW));
        assertThat(e).isNotNull();
        assertThat(e.code()).isEqualTo(ErrorCode.AI_INPUT_INVALID);
        assertThat(e.details()).containsExactly(new FieldDetail("scheduledAt"));
    }

    @Test void rejectsNull() { assertRejected(null); }

    @Test void acceptsExactlyMinLead() {
        assertThatCode(() -> window.requireWithin(NOW.plus(Duration.ofMinutes(1)), NOW)).doesNotThrowAnyException();
    }

    @Test void rejectsOneSecondBeforeMinLead() {
        assertRejected(NOW.plus(Duration.ofMinutes(1)).minusSeconds(1));
    }

    @Test void acceptsExactlyMaxLead() {
        assertThatCode(() -> window.requireWithin(NOW.plus(Duration.ofDays(30)), NOW)).doesNotThrowAnyException();
    }

    @Test void rejectsOneSecondAfterMaxLead() {
        assertRejected(NOW.plus(Duration.ofDays(30)).plusSeconds(1));
    }
}
