package com.ccapp.identity.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateToken() {
        log.debug("Identity refresh-token generateToken");
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        log.debug("Identity refresh-token hash tokenLength={}", rawToken == null ? 0 : rawToken.length());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Identity refresh-token hash failed: SHA-256 unavailable", e);
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
