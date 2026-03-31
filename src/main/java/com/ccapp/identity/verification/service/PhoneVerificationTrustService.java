package com.ccapp.identity.verification.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.domain.entity.UserSession;
import com.ccapp.identity.repository.UserDeviceRepository;
import com.ccapp.identity.repository.UserSessionRepository;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneVerificationTrustService {

    private final AccountHolderRepository accountHolderRepository;
    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserDeviceRepository userDeviceRepository;

    @Transactional
    public void markPhoneVerified(UUID accountHolderId,
                                  String phoneNumber,
                                  PhoneVerificationSource source,
                                  String ipAddress) {
        AccountHolder accountHolder = accountHolderRepository.findById(accountHolderId).orElse(null);
        if (accountHolder == null) {
            return;
        }

        String deviceIdentifier = markMostRecentDeviceTrusted(accountHolderId);
        accountHolder.setPhoneNumber(normalizeOptional(phoneNumber));
        accountHolder.setPhoneVerifiedAt(OffsetDateTime.now());
        accountHolder.setPhoneVerificationSource(source);
        accountHolder.setPhoneVerificationIp(normalizeOptional(ipAddress));
        accountHolder.setPhoneVerificationDevice(deviceIdentifier);
        accountHolderRepository.save(accountHolder);

        log.info("phone_verification_trust_updated accountHolderId={} source={} deviceIdentifier={}",
                accountHolderId,
                source,
                maskDeviceIdentifier(deviceIdentifier));
    }

    private String markMostRecentDeviceTrusted(UUID accountHolderId) {
        AppUser appUser = appUserRepository.findFirstByAccountHolderIdOrderByCreatedAtAsc(accountHolderId).orElse(null);
        if (appUser == null) {
            return null;
        }
        UserSession latestSession = userSessionRepository.findTopByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(appUser.getId()).orElse(null);
        if (latestSession == null || latestSession.getDevice() == null) {
            return null;
        }
        UserDevice device = latestSession.getDevice();
        device.setTrustedDevice(true);
        userDeviceRepository.save(device);
        return device.getDeviceIdentifier();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String maskDeviceIdentifier(String deviceIdentifier) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
            return "***";
        }
        if (deviceIdentifier.length() <= 6) {
            return "***" + deviceIdentifier.charAt(deviceIdentifier.length() - 1);
        }
        return deviceIdentifier.substring(0, 3) + "***" + deviceIdentifier.substring(deviceIdentifier.length() - 3);
    }
}
