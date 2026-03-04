package com.ccapp.identity.service;

import com.ccapp.common.exception.UnauthorizedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "firebase.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DevTokenVerifier implements FirebaseTokenVerifier {

    @Override
    public FirebaseUserClaims verify(String token) {
        if (token != null && token.startsWith("dev:")) {
            String uid = token.substring(4).trim();
            if (!uid.isEmpty()) {
                return new FirebaseUserClaims(uid, uid + "@dev.local");
            }
        }
        throw new UnauthorizedException("Firebase auth disabled; use Bearer dev:<uid>");
    }
}
