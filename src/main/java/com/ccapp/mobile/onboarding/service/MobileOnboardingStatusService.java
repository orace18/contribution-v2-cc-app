package com.ccapp.mobile.onboarding.service;

import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.dto.OnboardingStatusResponse;
import com.ccapp.identity.service.IdentityOnboardingService;
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
    private final IdentityOnboardingService identityOnboardingService;
    private final OnboardingStatusMapper onboardingStatusMapper;

    @Transactional(readOnly = true)
    public MobileOnboardingStatusResponse getStatus(@Nullable UUID userId, UUID accountHolderId) {
        MobileOnboardingStatusResponse journeyStatus =
                onboardingStatusMapper.normalize(mobileOnboardingJourneyService.getStatus(accountHolderId));

        if (userId == null) {
            return journeyStatus;
        }

        OnboardingStatusResponse identityStatus = identityOnboardingService.getOnboardingStatus(userId);
        if (isIdentityCompleted(identityStatus)) {
            return onboardingStatusMapper.toCompletedStatus(journeyStatus);
        }

        return journeyStatus;
    }

    private boolean isIdentityCompleted(OnboardingStatusResponse identityStatus) {
        return identityStatus != null
                && (identityStatus.onboardingCompleted()
                || identityStatus.onboardingStep() == OnboardingStep.COMPLETED);
    }
}
