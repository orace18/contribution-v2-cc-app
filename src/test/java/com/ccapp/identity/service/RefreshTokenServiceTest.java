package com.ccapp.identity.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenServiceTest {

    private final RefreshTokenService refreshTokenService = new RefreshTokenService();

    @Test
    void generateToken_shouldReturnNonEmptyUrlSafeValue() {
        String token = refreshTokenService.generateToken();

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertFalse(token.contains("="));
        assertFalse(token.contains("+"));
        assertFalse(token.contains("/"));
    }

    @Test
    void hash_shouldBeDeterministic_forSameInput() {
        String token = "same-token";

        String first = refreshTokenService.hash(token);
        String second = refreshTokenService.hash(token);

        assertEquals(first, second);
    }

    @Test
    void hash_shouldDiffer_forDifferentInputs() {
        String first = refreshTokenService.hash("token-1");
        String second = refreshTokenService.hash("token-2");

        assertNotEquals(first, second);
    }
}
