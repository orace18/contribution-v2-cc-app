package com.ccapp.mobile.payments.service;

import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateRequest;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobilePaymentService {

    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @Transactional
    public MobilePaymentInitiateResponse initiate(UUID actorUserId,
                                                  UUID accountHolderId,
                                                  MobilePaymentInitiateRequest request) {
        return mobileOnboardingJourneyService.initiatePaidPlanPayment(
                actorUserId,
                accountHolderId,
                request == null ? null : request.idempotencyKey()
        );
    }
}
