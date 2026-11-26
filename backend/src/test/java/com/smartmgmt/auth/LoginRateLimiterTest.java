package com.smartmgmt.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    @Test
    void blocksAfterTheWindowIsFullAndAllowsAgainOnceItSlides() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(2, Duration.ofMinutes(1), clock);

        assertTrue(limiter.allow("10.0.0.1"));
        assertTrue(limiter.allow("10.0.0.1"));
        assertFalse(limiter.allow("10.0.0.1"));
        assertTrue(limiter.allow("10.0.0.2"));

        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.allow("10.0.0.1"));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
