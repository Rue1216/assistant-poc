package com.example.copilot.aiassistant.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PerUserRateLimiterTest {

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final PerUserRateLimiter limiter = new PerUserRateLimiter(clock, 10);

    @Test
    void allowsTenPerMinuteThenRejects() {
        for (int i = 1; i <= 10; i++) {
            assertThat(limiter.tryAcquire("u1")).as("call %d", i).isTrue();
        }
        assertThat(limiter.tryAcquire("u1")).isFalse();
    }

    @Test
    void windowSlides() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("u1");
        }
        assertThat(limiter.tryAcquire("u1")).isFalse();
        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquire("u1")).isTrue();
    }

    @Test
    void usersAreIndependent() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("u1");
        }
        assertThat(limiter.tryAcquire("u1")).isFalse();
        assertThat(limiter.tryAcquire("u2")).isTrue();
    }
}
