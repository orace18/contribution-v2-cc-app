package com.ccapp.identity.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentIdentityService {

    private final UserProvisioningService userProvisioningService;

    @Transactional
    public AppUser resolveCurrentAppUser() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        if (principal.firebaseUid() == null || principal.firebaseUid().isBlank()) {
            throw new UnauthorizedException("Firebase identity is required");
        }
        return userProvisioningService.resolveForClaims(new FirebaseUserClaims(
                principal.firebaseUid(),
                principal.email(),
                principal.phoneNumber()
        ));
    }
}
