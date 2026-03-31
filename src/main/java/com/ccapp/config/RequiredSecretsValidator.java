package com.ccapp.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequiredSecretsValidator {

    private static final List<String> REQUIRED_SECRET_PROPERTIES = List.of(
            "jwt.secret",
            "admin.jwt.secret",
            "admin.bootstrap.password",
            "psp.jawupay.hmac-secret",
            "security.internal-api-key",
            "spring.datasource.password",
            "partner.api.key",
            "media.storage.access-key",
            "media.storage.secret-key",
            "spring.mail.password",
            "spring.activemq.password"
    );

    private final Environment environment;

    @PostConstruct
    void validateRequiredSecrets() {
        List<String> missing = new ArrayList<>();
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        boolean firebaseAuthEnabled = isFirebaseAuthEnabled();

        for (String propertyName : REQUIRED_SECRET_PROPERTIES) {
            String value = environment.getProperty(propertyName);
            if (isMissing(value)) {
                missing.add(propertyName);
            }
        }

        if (prod && !firebaseAuthEnabled) {
            throw new IllegalStateException("Firebase auth must be enabled in PROD");
        }

        if (firebaseAuthEnabled) {
            if (isMissing(environment.getProperty("firebase.project-id"))) {
                missing.add("firebase.project-id");
            }
            if (isMissing(environment.getProperty("firebase.credentials.path"))
                    && isMissing(environment.getProperty("firebase.credentials.json"))
                    && isMissing(environment.getProperty("firebase.credentials.base64"))) {
                missing.add("firebase.credentials.path|firebase.credentials.json|firebase.credentials.base64");
            }
        }

        if (!missing.isEmpty()) {
            if (prod && missing.stream().anyMatch(item -> item.startsWith("firebase."))) {
                log.error("Missing Firebase credentials in PROD");
            }
            throw new IllegalStateException(
                    "Missing required secret configuration: " + String.join(", ", missing)
                            + ". Provide values through environment variables."
            );
        }

        if (firebaseAuthEnabled) {
            log.info("Firebase auth ENABLED - strict verification");
        } else if (!prod) {
            log.warn("Firebase auth DISABLED - running in dev bypass mode");
        }
    }

    private boolean isFirebaseAuthEnabled() {
        return Boolean.parseBoolean(environment.getProperty("firebase.auth.enabled", "false"));
    }

    private boolean isMissing(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("${") && trimmed.endsWith("}");
    }
}
