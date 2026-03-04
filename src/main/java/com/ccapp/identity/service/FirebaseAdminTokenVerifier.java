package com.ccapp.identity.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "firebase.auth.enabled", havingValue = "true")
public class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {

    @Override
    public FirebaseUserClaims verify(String token) {
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token, true);
            return new FirebaseUserClaims(decodedToken.getUid(), decodedToken.getEmail());
        } catch (FirebaseAuthException e) {
            throw new UnauthorizedException("Invalid Firebase token");
        }
    }
}
