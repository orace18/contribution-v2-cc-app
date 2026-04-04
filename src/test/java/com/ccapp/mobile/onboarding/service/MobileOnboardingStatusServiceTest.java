package com.ccapp.mobile.onboarding.service;

import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.dto.OnboardingStatusResponse;
import com.ccapp.mobile.onboarding.dto.MobileOnboardingStatusResponse;
import com.ccapp.mobile.onboarding.mapper.OnboardingStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileOnboardingStatusServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACCOUNT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private MobileOnboardingJourneyService mobileOnboardingJourneyService;
    @Mock
    private com.ccapp.identity.service.IdentityOnboardingService identityOnboardingService;

    private MobileOnboardingStatusService mobileOnboardingStatusService;

    @BeforeEach
    void setUp() {
        mobileOnboardingStatusService = new MobileOnboardingStatusService(
                mobileOnboardingJourneyService,
                identityOnboardingService,
                new OnboardingStatusMapper()
        );
    }

    @Test
    void getStatus_identityCompleted_shouldOverrideJourneyToCompleted() {
        when(mobileOnboardingJourneyService.getStatus(ACCOUNT_ID)).thenReturn(new MobileOnboardingStatusResponse(
                "COUNTRY_SELECTED",
                List.of("REGISTERED", "AUTHENTICATED", "COUNTRY_SELECTED"),
                new MobileOnboardingStatusResponse.NextStep(
                        "SELECT_PLAN",
                        "POST",
                        "/api/mobile/onboarding/select-plan",
                        "Select a subscription plan"
                ),
                null,
                false,
                false,
                null,
                null
        ));
        when(identityOnboardingService.getOnboardingStatus(USER_ID)).thenReturn(identityCompleted());

        MobileOnboardingStatusResponse response = mobileOnboardingStatusService.getStatus(USER_ID, ACCOUNT_ID);

        assertEquals("COMPLETED", response.currentStatus());
        assertEquals(new BigDecimal("100.00"), response.progressPercentage());
        assertNotNull(response.nextStep());
        assertEquals("NONE", response.nextStep().code());
        assertTrue(response.completedSteps().contains("ONBOARDING_COMPLETED"));
        assertTrue(response.completedSteps().contains("COMPLETED"));
        assertFalse(response.isActive());
        assertFalse(response.isNetworkReady());
    }

    @Test
    void getStatus_identityIncomplete_shouldKeepJourneyStatus() {
        MobileOnboardingStatusResponse journeyStatus = new MobileOnboardingStatusResponse(
                "COUNTRY_SELECTED",
                List.of("REGISTERED", "AUTHENTICATED", "COUNTRY_SELECTED"),
                new MobileOnboardingStatusResponse.NextStep(
                        "SELECT_PLAN",
                        "POST",
                        "/api/mobile/onboarding/select-plan",
                        "Select a subscription plan"
                ),
                null,
                false,
                false,
                null,
                null
        );
        when(mobileOnboardingJourneyService.getStatus(ACCOUNT_ID)).thenReturn(journeyStatus);
        when(identityOnboardingService.getOnboardingStatus(USER_ID)).thenReturn(new OnboardingStatusResponse(
                USER_ID,
                OnboardingStep.STEP_2,
                2,
                3,
                new BigDecimal("33.33"),
                true,
                false,
                false,
                false,
                null,
                null
        ));

        MobileOnboardingStatusResponse response = mobileOnboardingStatusService.getStatus(USER_ID, ACCOUNT_ID);

        assertEquals("COUNTRY_SELECTED", response.currentStatus());
        assertNull(response.progressPercentage());
        assertNotNull(response.nextStep());
        assertEquals("SELECT_PLAN", response.nextStep().code());
    }

    @Test
    void getStatus_withoutUserId_shouldKeepBackwardCompatibleFallbackAndNormalizeLegacyCompleted() {
        when(mobileOnboardingJourneyService.getStatus(ACCOUNT_ID)).thenReturn(new MobileOnboardingStatusResponse(
                "ONBOARDING_COMPLETED",
                List.of("REGISTERED", "ONBOARDING_COMPLETED"),
                null,
                null,
                true,
                true,
                null,
                null
        ));

        MobileOnboardingStatusResponse response = mobileOnboardingStatusService.getStatus(null, ACCOUNT_ID);

        assertEquals("COMPLETED", response.currentStatus());
        assertEquals(new BigDecimal("100.00"), response.progressPercentage());
        assertEquals("NONE", response.nextStep().code());
        assertTrue(response.completedSteps().contains("ONBOARDING_COMPLETED"));
        assertTrue(response.completedSteps().contains("COMPLETED"));
        verifyNoInteractions(identityOnboardingService);
    }

    private OnboardingStatusResponse identityCompleted() {
        return new OnboardingStatusResponse(
                USER_ID,
                OnboardingStep.COMPLETED,
                3,
                3,
                new BigDecimal("100.00"),
                true,
                true,
                true,
                true,
                OffsetDateTime.now(),
                null
        );
    }
}
