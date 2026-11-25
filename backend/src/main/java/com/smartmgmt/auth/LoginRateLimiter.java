package com.smartmgmt.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fixed window of login attempts per key (the client address). In-memory, so
 * it resets on restart and is not shared across instances.
 */
@Component
public class LoginRateLimiter {

    private final int maxPerWindow;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${app.auth.login-max-per-minute:10}") int maxPerMinute) {
        this(maxPerMinute, Duration.ofMinutes(1), Clock.systemUTC());
    }

    LoginRateLimiter(int maxPerWindow, Duration window, Clock clock) {
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.clock = clock;
    }

    public boolean allow(String key) {
        long now = clock.millis();
        long cutoff = now - window.toMillis();
        ArrayDeque<Long> deque = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                deque.removeFirst();
            }
            if (deque.size() >= maxPerWindow) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
