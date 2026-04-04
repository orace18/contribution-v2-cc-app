package com.ccapp.mobile.onboarding.service;

import com.ccapp.mobile.onboarding.dto.MobileOnboardingStatusResponse;
import com.ccapp.mobile.onboarding.mapper.OnboardingStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileOnboardingStatusService {

    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;
    private final OnboardingStatusMapper onboardingStatusMapper;

    @Transactional(readOnly = true)
    public MobileOnboardingStatusResponse getStatus(@Nullable UUID userId, UUID accountHolderId) {
        return onboardingStatusMapper.normalize(mobileOnboardingJourneyService.getStatus(accountHolderId));
    }
}
