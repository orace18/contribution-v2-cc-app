package com.ccapp.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
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
        for (String propertyName : REQUIRED_SECRET_PROPERTIES) {
            String value = environment.getProperty(propertyName);
            if (isMissing(value)) {
                missing.add(propertyName);
            }
        }

        if (isFirebaseAuthEnabled()
                && isMissing(environment.getProperty("firebase.credentials.path"))
                && isMissing(environment.getProperty("firebase.credentials.base64"))) {
            missing.add("firebase.credentials.path|firebase.credentials.base64");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required secret configuration: " + String.join(", ", missing)
                            + ". Provide values through environment variables."
            );
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
