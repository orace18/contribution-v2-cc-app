package com.ccapp.identity.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@ConditionalOnProperty(name = "firebase.auth.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DisabledFirebaseTokenVerifier implements FirebaseTokenVerifier {

    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Value("${firebase.dev-bypass.uid:}")
    private String devBypassUid;

    @Value("${firebase.dev-bypass.email:}")
    private String devBypassEmail;

    @PostConstruct
    void logMode() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            log.warn("Firebase auth DISABLED - running in dev bypass mode");
        }
    }

    @Override
    public FirebaseUserClaims verify(String token) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new UnauthorizedException("Firebase authentication must be enabled in production");
        }

        if (token != null && !token.isBlank()) {
            FirebaseUserClaims claims = extractUnverifiedClaims(token.trim());
            log.warn("Firebase auth DISABLED - running in dev bypass mode source=token-claims uid={}",
                    claims.uid());
            return claims;
        }

        String mockUid = normalizeOptional(devBypassUid);
        if (mockUid != null) {
            log.warn("Firebase auth DISABLED - running in dev bypass mode source=mock-user uid={}", mockUid);
            return new FirebaseUserClaims(mockUid, normalizeOptional(devBypassEmail), null);
        }

        throw new UnauthorizedException("Firebase auth disabled in dev mode and no token or mock user is configured");
    }

    private FirebaseUserClaims extractUnverifiedClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new UnauthorizedException("Malformed Firebase token");
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
            String uid = firstNonBlank(
                    claims.path("user_id").asText(null),
                    claims.path("uid").asText(null),
                    claims.path("sub").asText(null)
            );
            if (uid == null) {
                throw new UnauthorizedException("Firebase token missing uid");
            }
            return new FirebaseUserClaims(
                    uid,
                    normalizeOptional(claims.path("email").asText(null)),
                    normalizeOptional(claims.path("phone_number").asText(null))
            );
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Malformed Firebase token");
        } catch (Exception ex) {
            throw new UnauthorizedException("Unable to read Firebase token in dev bypass mode");
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
