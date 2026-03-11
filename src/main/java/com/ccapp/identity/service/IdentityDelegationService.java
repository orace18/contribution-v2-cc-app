package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.service.DelegationService;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.dto.LinkAssistedAccountRequest;
import com.ccapp.identity.dto.LinkAssistedAccountResponse;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityDelegationService {

    private final UserRepository userRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AppUserRepository appUserRepository;
    private final UserProfileRepository userProfileRepository;
    private final DelegationService delegationService;

    @Transactional
    public LinkAssistedAccountResponse linkAssistedAccount(String firebaseUid,
                                                           String email,
                                                           LinkAssistedAccountRequest request) {
        log.info("Identity linkAssistedAccount start firebaseUid={} phone={}", firebaseUid, request.phoneNumber());
        if (firebaseUid == null || firebaseUid.isBlank()) {
            throw new UnauthorizedException("Firebase identity is required");
        }

        String normalizedPhone = request.phoneNumber().trim();
        AccountHolder accountHolder = accountHolderRepository.findByPhoneNumber(normalizedPhone)
                .orElseThrow(() -> new NotFoundException("Account holder not found"));
        if (accountHolder.getAccountType() != AccountType.ASSISTED) {
            throw new BusinessException("ASSISTED_ACCOUNT_REQUIRED");
        }

        Optional<AppUser> existingByFirebaseUid = appUserRepository.findByFirebaseUid(firebaseUid);
        if (existingByFirebaseUid.isPresent()
                && !existingByFirebaseUid.get().getAccountHolderId().equals(accountHolder.getId())) {
            throw new ConflictException("Firebase account already linked");
        }

        User user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> userRepository.save(User.builder()
                        .firebaseUid(firebaseUid)
                        .email(email)
                        .role(UserRole.USER)
                        .accountStatus(AccountStatus.ACTIVE)
                        .kycStatus(accountHolder.getKycStatus())
                        .build()));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Account is not active");
        }
        if (email != null && !email.isBlank() && (user.getEmail() == null || user.getEmail().isBlank())) {
            user.setEmail(email);
        }
        user.setKycStatus(accountHolder.getKycStatus());
        userRepository.save(user);

        appUserRepository.findById(user.getId())
                .filter(existing -> !existing.getFirebaseUid().equals(firebaseUid))
                .ifPresent(existing -> {
                    throw new ConflictException("Identity already linked");
                });

        AppUser appUser = existingByFirebaseUid.orElseGet(() -> appUserRepository.save(AppUser.builder()
                .id(user.getId())
                .firebaseUid(firebaseUid)
                .accountHolderId(accountHolder.getId())
                .role(UserRole.USER)
                .createdAt(OffsetDateTime.now())
                .build()));
        if (!appUser.getId().equals(user.getId())) {
            throw new ConflictException("Inconsistent identity mapping");
        }
        if (!appUser.getAccountHolderId().equals(accountHolder.getId())) {
            throw new ConflictException("Account already linked to a different holder");
        }

        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> UserProfile.builder()
                        .user(user)
                        .onboardingStep(OnboardingStep.STEP_1)
                        .onboardingCompleted(false)
                        .build());
        profile.setFirstName(accountHolder.getFirstName());
        profile.setLastName(accountHolder.getLastName());
        profile.setPhone(accountHolder.getPhoneNumber() != null ? accountHolder.getPhoneNumber() : "");
        profile.setCountry(accountHolder.getCountryCode());
        userProfileRepository.save(profile);

        delegationService.deactivateActiveDelegations(accountHolder.getId());
        accountHolder.setAccountType(AccountType.STANDARD);
        accountHolderRepository.save(accountHolder);

        log.info("Identity linkAssistedAccount success firebaseUid={} appUserId={} accountHolderId={}",
                firebaseUid, appUser.getId(), accountHolder.getId());
        return new LinkAssistedAccountResponse(
                appUser.getId(),
                accountHolder.getId(),
                accountHolder.getAccountType(),
                accountHolder.getCountryCode(),
                accountHolder.getServiceCountryCode(),
                OffsetDateTime.now()
        );
    }
}
