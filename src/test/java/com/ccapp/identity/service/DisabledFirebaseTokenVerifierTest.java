package com.ccapp.identity.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisabledFirebaseTokenVerifierTest {

    @Test
    void verify_shouldExtractUnverifiedClaimsInDevBypassMode() {
        DisabledFirebaseTokenVerifier verifier = new DisabledFirebaseTokenVerifier(
                new ObjectMapper(),
                new MockEnvironment().withProperty("spring.profiles.active", "dev")
        );

        FirebaseUserClaims claims = verifier.verify(jwt(
                "{\"alg\":\"RS256\"}",
                "{\"user_id\":\"firebase-user-1\",\"email\":\"dev@example.com\",\"phone_number\":\"+22961000000\"}"
        ));

        assertEquals("firebase-user-1", claims.uid());
        assertEquals("dev@example.com", claims.email());
        assertEquals("+22961000000", claims.phoneNumber());
    }

    @Test
    void verify_shouldUseConfiguredMockUserWhenTokenMissing() {
        DisabledFirebaseTokenVerifier verifier = new DisabledFirebaseTokenVerifier(
                new ObjectMapper(),
                new MockEnvironment().withProperty("spring.profiles.active", "dev")
        );
        ReflectionTestUtils.setField(verifier, "devBypassUid", "mock-user");
        ReflectionTestUtils.setField(verifier, "devBypassEmail", "mock@example.com");

        FirebaseUserClaims claims = verifier.verify("");

        assertEquals("mock-user", claims.uid());
        assertEquals("mock@example.com", claims.email());
    }

    @Test
    void verify_shouldRejectMissingTokenWhenNoMockUserConfigured() {
        DisabledFirebaseTokenVerifier verifier = new DisabledFirebaseTokenVerifier(
                new ObjectMapper(),
                new MockEnvironment().withProperty("spring.profiles.active", "dev")
        );

        assertThrows(UnauthorizedException.class, () -> verifier.verify(""));
    }

    private String jwt(String header, String payload) {
        return encode(header) + "." + encode(payload) + ".signature";
    }

    private String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
