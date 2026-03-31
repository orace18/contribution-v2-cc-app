package com.ccapp.identity.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "firebase.auth.enabled", havingValue = "true")
@Slf4j
public class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {

    @Value("${firebase.project-id:}")
    private String firebaseProjectId;

    @Override
    public FirebaseUserClaims verify(String token) {
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token, true);
            validateProject(decodedToken);
            if (decodedToken.getUid() == null || decodedToken.getUid().isBlank()) {
                throw new UnauthorizedException("Firebase token missing uid");
            }
            Map<String, Object> claims = decodedToken.getClaims();
            String phoneNumber = stringClaim(claims, "phone_number");
            log.info("firebase_auth_verified source=FIREBASE_AUTH uid={} projectId={}",
                    decodedToken.getUid(),
                    normalizeOptional(firebaseProjectId));
            return new FirebaseUserClaims(decodedToken.getUid(), decodedToken.getEmail(), phoneNumber);
        } catch (FirebaseAuthException e) {
            log.warn(
                    "Firebase token verification failed authErrorCode={} message={}",
                    e.getAuthErrorCode(),
                    e.getMessage()
            );
            throw new UnauthorizedException("Invalid Firebase token");
        }
    }

    private void validateProject(FirebaseToken decodedToken) {
        if (firebaseProjectId == null || firebaseProjectId.isBlank()) {
            return;
        }
        String expectedIssuer = "https://securetoken.google.com/" + firebaseProjectId;
        String audience = stringClaim(decodedToken.getClaims(), "aud");
        String issuer = stringClaim(decodedToken.getClaims(), "iss");
        if (!firebaseProjectId.equals(audience) || !expectedIssuer.equals(issuer)) {
            throw new UnauthorizedException("Firebase project mismatch");
        }
    }

    private String stringClaim(Map<String, Object> claims, String key) {
        if (claims == null) {
            return null;
        }
        Object value = claims.get(key);
        return value == null ? null : value.toString();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
