package com.example.copilot.aiassistant.interfaces.rest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Sliding 60-second window limiter, one window per user. */
public class PerUserRateLimiter {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final Clock clock;
    private final int perMinute;
    private final ConcurrentMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public PerUserRateLimiter(Clock clock, int perMinute) {
        this.clock = clock;
        this.perMinute = perMinute;
    }

    public boolean tryAcquire(String userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        Deque<Instant> window = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= perMinute) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
