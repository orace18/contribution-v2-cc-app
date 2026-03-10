package com.ccapp.identity.service;

import com.ccapp.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "firebase.auth.enabled", havingValue = "false")
@Slf4j
public class DevTokenVerifier implements FirebaseTokenVerifier {

    @Override
    public FirebaseUserClaims verify(String token) {
        log.debug("Identity dev-token verify invoked tokenPresent={}", token != null && !token.isBlank());
        if (token != null && token.startsWith("dev:")) {
            String uid = token.substring(4).trim();
            if (!uid.isEmpty()) {
                log.debug("Identity dev-token accepted uid={}", uid);
                return new FirebaseUserClaims(uid, uid + "@dev.local");
            }
        }
        log.warn("Identity dev-token rejected; expected format Bearer dev:<uid>");
        throw new UnauthorizedException("Firebase auth disabled; use Bearer dev:<uid>");
    }
}
