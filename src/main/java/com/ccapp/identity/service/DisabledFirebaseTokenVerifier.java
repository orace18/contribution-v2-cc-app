package com.ccapp.identity.service;

import com.ccapp.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "firebase.auth.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class DisabledFirebaseTokenVerifier implements FirebaseTokenVerifier {

    @Override
    public FirebaseUserClaims verify(String token) {
        log.warn("Firebase token verification requested while firebase.auth.enabled=false");
        throw new UnauthorizedException("Firebase authentication is disabled for this environment");
    }
}
