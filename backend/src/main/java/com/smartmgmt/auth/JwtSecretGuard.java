package com.smartmgmt.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The dev signing key is in git. A prod process that forgets JWT_SECRET must
 * not boot, or anyone who read the repo can mint tokens.
 */
@Component
public class JwtSecretGuard implements ApplicationRunner {

    static final String DEV_SECRET = "dev-only-secret-do-not-use-in-production-change-me-1234567890";

    private final Environment environment;
    private final JwtProperties properties;

    public JwtSecretGuard(Environment environment, JwtProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = false;
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                prod = true;
                break;
            }
        }
        if (prod && DEV_SECRET.equals(properties.getSecret())) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the dev default. Set a 32+ byte secret before starting with the prod profile.");
        }
    }
}
