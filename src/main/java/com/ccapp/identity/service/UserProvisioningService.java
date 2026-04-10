package com.ccapp.identity.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.enums.UserRole;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    private final AccountHolderRepository accountHolderRepository;
    private final AppUserRepository appUserRepository;

    @Transactional
    public AppUser provisionNew(FirebaseUserClaims claims) {
        UUID id = UUID.randomUUID();
        String name = claims.email() != null
                ? claims.email().split("@")[0] : "user";

        accountHolderRepository.save(AccountHolder.builder()
                .id(id)
                .firstName(name)
                .lastName("")
                .countryCode("INT")
                .serviceCountryCode("INT")
                .phoneNumber(claims.phoneNumber())
                .build());

        return appUserRepository.save(AppUser.builder()
                .id(id)
                .firebaseUid(claims.uid())
                .accountHolderId(id)
                .role(UserRole.USER)
                .build());
    }

    @Transactional
    public AppUser provisionFromLegacy(UUID legacyUserId, FirebaseUserClaims claims) {
        return appUserRepository.save(AppUser.builder()
                .id(legacyUserId)
                .firebaseUid(claims.uid())
                .accountHolderId(legacyUserId)
                .role(UserRole.USER)
                .build());
    }
}
