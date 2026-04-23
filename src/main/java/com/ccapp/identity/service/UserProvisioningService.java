package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProvisioningService {

    private final AccountHolderRepository accountHolderRepository;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;

    @Transactional
    public AppUser resolveForClaims(FirebaseUserClaims claims) {
        String firebaseUid = normalizeRequired(claims.uid(), "Firebase uid is required");

        Optional<AppUser> appUserByUid = appUserRepository.findByFirebaseUid(firebaseUid);
        Optional<User> userByUid = userRepository.findByFirebaseUid(firebaseUid);

        if (appUserByUid.isPresent()) {
            return ensureCanonicalUserForAppUser(appUserByUid.get(), userByUid.orElse(null), claims);
        }
        if (userByUid.isPresent()) {
            return provisionFromLegacy(userByUid.get().getId(), claims);
        }
        return provisionNew(claims);
    }

    @Transactional
    public AppUser provisionNew(FirebaseUserClaims claims) {
        UUID id = UUID.randomUUID();
        String email = normalizeOptional(claims.email());
        assertEmailNotBoundToAnotherUser(email, id, claims.uid());
        assertPhoneNotBoundToAnotherAccountHolder(normalizeOptional(claims.phoneNumber()), id, claims.uid());
        User user = userRepository.save(newUser(id, claims.uid(), email));
        String name = email != null ? email.split("@")[0] : "user";

        accountHolderRepository.save(AccountHolder.builder()
                .id(user.getId())
                .firstName(name)
                .lastName("")
                .countryCode("INT")
                .serviceCountryCode("INT")
                .phoneNumber(claims.phoneNumber())
                .accountType(AccountType.STANDARD)
                .createdVia(AccountCreatedVia.SELF)
                .kycStatus(KycStatus.NOT_STARTED)
                .kycLevel(KycLevel.LEVEL_0_UNVERIFIED)
                .build());

        return appUserRepository.save(AppUser.builder()
                .id(user.getId())
                .firebaseUid(claims.uid())
                .accountHolderId(user.getId())
                .role(UserRole.USER)
                .build());
    }

    @Transactional
    public AppUser provisionFromLegacy(UUID legacyUserId, FirebaseUserClaims claims) {
        User user = userRepository.findById(legacyUserId)
                .orElseGet(() -> userRepository.save(newUser(legacyUserId, claims.uid(), normalizeOptional(claims.email()))));
        assertSameFirebaseUid(user, claims.uid());
        syncEmailIfSafe(user, claims.email());
        ensureAccountHolder(user.getId(), claims);
        return appUserRepository.save(AppUser.builder()
                .id(user.getId())
                .firebaseUid(claims.uid())
                .accountHolderId(user.getId())
                .role(user.getRole() == null ? UserRole.USER : user.getRole())
                .build());
    }

    private AppUser ensureCanonicalUserForAppUser(AppUser appUser, User userByUid, FirebaseUserClaims claims) {
        if (userByUid != null && !userByUid.getId().equals(appUser.getId())) {
            log.warn("identity graph ambiguous firebaseUid={} appUserId={} accountHolderId={} userIdByUid={}",
                    claims.uid(), appUser.getId(), appUser.getAccountHolderId(), userByUid.getId());
            throw new BusinessException("IDENTITY_GRAPH_AMBIGUOUS");
        }

        User user = userByUid;
        if (user == null) {
            String email = normalizeOptional(claims.email());
            assertEmailNotBoundToAnotherUser(email, appUser.getId(), claims.uid());
            user = userRepository.save(newUser(appUser.getId(), claims.uid(), email));
            log.warn("identity graph repaired missing users row firebaseUid={} userId={} accountHolderId={}",
                    claims.uid(), user.getId(), appUser.getAccountHolderId());
        } else {
            syncEmailIfSafe(user, claims.email());
        }

        if (!appUser.getId().equals(user.getId())) {
            log.warn("identity graph ambiguous firebaseUid={} appUserId={} userId={}",
                    claims.uid(), appUser.getId(), user.getId());
            throw new BusinessException("IDENTITY_GRAPH_AMBIGUOUS");
        }

        requireAccountHolder(appUser, claims);
        return appUser;
    }

    private void requireAccountHolder(AppUser appUser, FirebaseUserClaims claims) {
        if (!accountHolderRepository.existsById(appUser.getAccountHolderId())) {
            log.warn("identity graph ambiguous firebaseUid={} appUserId={} missingAccountHolderId={}",
                    claims.uid(), appUser.getId(), appUser.getAccountHolderId());
            throw new BusinessException("IDENTITY_GRAPH_AMBIGUOUS");
        }
    }

    private void ensureAccountHolder(UUID accountHolderId, FirebaseUserClaims claims) {
        if (accountHolderRepository.existsById(accountHolderId)) {
            return;
        }
        assertPhoneNotBoundToAnotherAccountHolder(normalizeOptional(claims.phoneNumber()), accountHolderId, claims.uid());
        String email = normalizeOptional(claims.email());
        String name = email != null ? email.split("@")[0] : "user";
        accountHolderRepository.save(AccountHolder.builder()
                .id(accountHolderId)
                .firstName(name)
                .lastName("")
                .countryCode("INT")
                .serviceCountryCode("INT")
                .phoneNumber(claims.phoneNumber())
                .accountType(AccountType.STANDARD)
                .createdVia(AccountCreatedVia.SELF)
                .kycStatus(KycStatus.NOT_STARTED)
                .kycLevel(KycLevel.LEVEL_0_UNVERIFIED)
                .build());
        log.warn("identity graph repaired legacy user missing account_holder row firebaseUid={} accountHolderId={}",
                claims.uid(), accountHolderId);
    }

    private User newUser(UUID id, String firebaseUid, String email) {
        return User.builder()
                .id(id)
                .firebaseUid(firebaseUid)
                .email(email)
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build();
    }

    private void assertSameFirebaseUid(User user, String firebaseUid) {
        if (user.getFirebaseUid() != null && !user.getFirebaseUid().equals(firebaseUid)) {
            log.warn("identity graph ambiguous userId={} existingFirebaseUid={} requestedFirebaseUid={}",
                    user.getId(), user.getFirebaseUid(), firebaseUid);
            throw new BusinessException("IDENTITY_GRAPH_AMBIGUOUS");
        }
        user.setFirebaseUid(firebaseUid);
    }

    private void syncEmailIfSafe(User user, String email) {
        String normalizedEmail = normalizeOptional(email);
        if (normalizedEmail == null || normalizedEmail.equals(user.getEmail())) {
            return;
        }
        assertEmailNotBoundToAnotherUser(normalizedEmail, user.getId(), user.getFirebaseUid());
        user.setEmail(normalizedEmail);
        userRepository.save(user);
    }

    private void assertEmailNotBoundToAnotherUser(String email, UUID expectedUserId, String firebaseUid) {
        if (email == null) {
            return;
        }
        userRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            if (!existing.getId().equals(expectedUserId)) {
                log.warn("identity email conflict email={} existingUserId={} expectedUserId={} firebaseUid={}",
                        email, existing.getId(), expectedUserId, firebaseUid);
                throw new BusinessException("IDENTITY_EMAIL_ALREADY_LINKED");
            }
        });
    }

    private void assertPhoneNotBoundToAnotherAccountHolder(String phoneNumber, UUID expectedAccountHolderId, String firebaseUid) {
        if (phoneNumber == null) {
            return;
        }
        accountHolderRepository.findByPhoneNumber(phoneNumber).ifPresent(existing -> {
            if (!existing.getId().equals(expectedAccountHolderId)) {
                log.warn("identity phone conflict phone={} existingAccountHolderId={} expectedAccountHolderId={} firebaseUid={}",
                        phoneNumber, existing.getId(), expectedAccountHolderId, firebaseUid);
                throw new BusinessException("IDENTITY_PHONE_ALREADY_LINKED");
            }
        });
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
