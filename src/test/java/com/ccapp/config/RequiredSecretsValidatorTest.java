package com.ccapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredSecretsValidatorTest {

    @Test
    void validateRequiredSecrets_shouldThrow_whenARequiredSecretIsMissing() {
        MockEnvironment environment = baseEnvironment();
        environment.setProperty("partner.api.key", "");
        RequiredSecretsValidator validator = new RequiredSecretsValidator(environment);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateRequiredSecrets);

        assertTrue(ex.getMessage().contains("partner.api.key"));
    }

    @Test
    void validateRequiredSecrets_shouldPass_whenAllRequiredSecretsArePresent() {
        MockEnvironment environment = baseEnvironment();
        RequiredSecretsValidator validator = new RequiredSecretsValidator(environment);

        assertDoesNotThrow(validator::validateRequiredSecrets);
    }

    @Test
    void validateRequiredSecrets_shouldPass_whenFirebaseAuthIsDisabledAndCredentialsAreMissing() {
        MockEnvironment environment = baseEnvironment();
        environment.setProperty("firebase.auth.enabled", "false");
        environment.setProperty("firebase.credentials.path", "");
        environment.setProperty("firebase.credentials.json", "");
        environment.setProperty("firebase.credentials.base64", "");
        RequiredSecretsValidator validator = new RequiredSecretsValidator(environment);

        assertDoesNotThrow(validator::validateRequiredSecrets);
    }

    @Test
    void validateRequiredSecrets_shouldPass_whenFirebaseAuthUsesInlineJsonCredentials() {
        MockEnvironment environment = baseEnvironment();
        environment.setProperty("firebase.credentials.path", "");
        environment.setProperty("firebase.credentials.json", "{\"type\":\"service_account\"}");
        environment.setProperty("firebase.credentials.base64", "");
        RequiredSecretsValidator validator = new RequiredSecretsValidator(environment);

        assertDoesNotThrow(validator::validateRequiredSecrets);
    }

    @Test
    void validateRequiredSecrets_shouldThrow_whenFirebaseAuthIsEnabledWithoutAnyCredentialSource() {
        MockEnvironment environment = baseEnvironment();
        environment.setProperty("firebase.credentials.path", "");
        environment.setProperty("firebase.credentials.json", "");
        environment.setProperty("firebase.credentials.base64", "");
        RequiredSecretsValidator validator = new RequiredSecretsValidator(environment);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateRequiredSecrets);

        assertTrue(ex.getMessage().contains("firebase.credentials.path|firebase.credentials.json|firebase.credentials.base64"));
    }

    @Test
    void validateRequiredSecrets_shouldThrow_whenFirebaseProjectIdIsMissingWhileAuthEnabled() {
        MockEnvironment environment = baseEnvironment();
        environment.setProperty("firebase.project-id", "");
        RequiredSecretsValidator validator = new RequiredSecretsValidator(environment);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateRequiredSecrets);

        assertTrue(ex.getMessage().contains("firebase.project-id"));
    }

    @Test
    void validateRequiredSecrets_shouldThrow_whenFirebaseAuthIsDisabledInProd() {
        MockEnvironment environment = baseEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("firebase.auth.enabled", "false");
        RequiredSecretsValidator validator = new RequiredSecretsValidator(environment);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateRequiredSecrets);

        assertTrue(ex.getMessage().contains("Firebase auth must be enabled in PROD"));
    }

    private MockEnvironment baseEnvironment() {
        return new MockEnvironment()
                .withProperty("firebase.auth.enabled", "true")
                .withProperty("jwt.secret", "jwt-secret")
                .withProperty("admin.jwt.secret", "admin-jwt-secret")
                .withProperty("admin.bootstrap.password", "bootstrap-password")
                .withProperty("firebase.project-id", "jawupay-bbc9f")
                .withProperty("firebase.credentials.path", "/opt/secrets/firebase.json")
                .withProperty("firebase.credentials.json", "")
                .withProperty("firebase.credentials.base64", "")
                .withProperty("psp.jawupay.hmac-secret", "jawupay-secret")
                .withProperty("security.internal-api-key", "internal-api-key")
                .withProperty("spring.datasource.password", "db-password")
                .withProperty("partner.api.key", "partner-api-key")
                .withProperty("media.storage.access-key", "media-access")
                .withProperty("media.storage.secret-key", "media-secret")
                .withProperty("spring.mail.password", "mail-password")
                .withProperty("spring.activemq.password", "activemq-password");
    }
}
