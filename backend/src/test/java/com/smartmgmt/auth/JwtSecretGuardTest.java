package com.smartmgmt.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class JwtSecretGuardTest {

    @Test
    void prodRefusesTheDevSecret() {
        JwtSecretGuard guard = guard("prod", JwtSecretGuard.DEV_SECRET);
        assertThatThrownBy(() -> guard.run(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodAllowsARealSecret() {
        JwtSecretGuard guard = guard("prod", "a-real-secret-that-is-at-least-32-bytes-long");
        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void localAllowsTheDevSecret() {
        JwtSecretGuard guard = guard("local", JwtSecretGuard.DEV_SECRET);
        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    private static JwtSecretGuard guard(String profile, String secret) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {profile});
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        return new JwtSecretGuard(environment, properties);
    }
}
