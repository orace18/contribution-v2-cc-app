package com.ccapp.devtools.service;

import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class DevToolsEnvironmentGuard {

    private static final String DISABLED_MESSAGE = "Dev tools are disabled in this environment";

    private final Environment environment;

    public DevToolsEnvironmentGuard(Environment environment) {
        this.environment = environment;
    }

    public void assertEnabled() {
        if (!isEnabled()) {
            throw new AccessDeniedException(DISABLED_MESSAGE);
        }
    }

    public boolean isEnabled() {
        return isDevProfileActive() || isDevEnvironment();
    }

    private boolean isDevProfileActive() {
        if (environment.matchesProfiles("dev")) {
            return true;
        }

        String configuredProfiles = environment.getProperty("spring.profiles.active");
        if (configuredProfiles == null || configuredProfiles.isBlank()) {
            return false;
        }

        for (String profile : configuredProfiles.split(",")) {
            if ("dev".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isDevEnvironment() {
        String appEnvironment = environment.getProperty("app.environment");
        return appEnvironment != null && "dev".equalsIgnoreCase(appEnvironment.trim());
    }
}
