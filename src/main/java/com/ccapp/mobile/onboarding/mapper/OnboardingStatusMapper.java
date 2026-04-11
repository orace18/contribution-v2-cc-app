package com.ccapp.mobile.onboarding.mapper;

import com.ccapp.mobile.onboarding.dto.MobileOnboardingStatusResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class OnboardingStatusMapper {

    static final String COMPLETED_STATUS = "COMPLETED";
    static final String LEGACY_COMPLETED_STATUS = "ONBOARDING_COMPLETED";
    private static final BigDecimal COMPLETE_PROGRESS = new BigDecimal("100.00");
    private static final MobileOnboardingStatusResponse.NextStep NO_NEXT_STEP =
            new MobileOnboardingStatusResponse.NextStep(
                    "NONE",
                    "",
                    "",
                    "Onboarding completed"
            );

    public MobileOnboardingStatusResponse normalize(@Nullable MobileOnboardingStatusResponse source) {
        if (source == null) {
            return null;
        }
        return isCompleted(source.currentStatus()) ? toCompletedStatus(source) : source;
    }

    public MobileOnboardingStatusResponse toCompletedStatus(@Nullable MobileOnboardingStatusResponse source) {
        List<String> completedSteps = new ArrayList<>();
        if (source != null && source.completedSteps() != null) {
            completedSteps.addAll(source.completedSteps());
        }
        if (!completedSteps.contains(LEGACY_COMPLETED_STATUS)) {
            completedSteps.add(LEGACY_COMPLETED_STATUS);
        }
        if (!completedSteps.contains(COMPLETED_STATUS)) {
            completedSteps.add(COMPLETED_STATUS);
        }

        return new MobileOnboardingStatusResponse(
                COMPLETED_STATUS,
                List.copyOf(completedSteps),
                NO_NEXT_STEP,
                COMPLETE_PROGRESS,
                source != null && source.isActive(),
                source != null && source.isNetworkReady(),
                source == null ? null : source.paymentOperationId(),
                source == null ? null : source.paymentReference(),
                source == null ? null : source.sponsorDecision(),
                source == null ? null : source.placementStatus()
        );
    }

    public boolean isCompleted(@Nullable String currentStatus) {
        return COMPLETED_STATUS.equalsIgnoreCase(currentStatus)
                || LEGACY_COMPLETED_STATUS.equalsIgnoreCase(currentStatus);
    }
}
