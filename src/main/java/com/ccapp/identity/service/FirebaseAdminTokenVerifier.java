package com.ccapp.identity.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {

    @Override
    public FirebaseUserClaims verify(String token) {
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token, true);
            return new FirebaseUserClaims(decodedToken.getUid(), decodedToken.getEmail());
        } catch (FirebaseAuthException e) {
            log.warn(
                    "Firebase token verification failed authErrorCode={} message={}",
                    e.getAuthErrorCode(),
                    e.getMessage()
            );
            throw new UnauthorizedException("Invalid Firebase token");
        }
    }
}
