package com.ccapp.mobile.onboarding.controller;

import com.ccapp.common.exception.GlobalExceptionHandler;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.identity.managedaccounts.service.ManagedAccountReadContextService;
import com.ccapp.mobile.onboarding.dto.MobileCountrySelectionResponse;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileOnboardingControllerTest {

    private final MobileOnboardingService mobileOnboardingService = mock(MobileOnboardingService.class);
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService = mock(MobileOnboardingJourneyService.class);
    private final MobileOnboardingStatusService mobileOnboardingStatusService = mock(MobileOnboardingStatusService.class);
    private final ManagedAccountReadContextService managedAccountReadContextService = mock(ManagedAccountReadContextService.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void selectCountryEndpoint_shouldReturnSuccessForAuthenticatedUser() throws Exception {
        UUID actorUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID accountHolderId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        setAuthenticatedPrincipal(actorUserId, accountHolderId);
        when(mobileOnboardingJourneyService.selectCountry(actorUserId, accountHolderId, "BJ"))
                .thenReturn(new MobileCountrySelectionResponse(accountHolderId, "BJ"));

        MockMvc mockMvc = mockMvcForController();

        mockMvc.perform(post("/api/mobile/onboarding/select-country")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"country":"BJ"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Country selected"))
                .andExpect(jsonPath("$.data.accountHolderId").value(accountHolderId.toString()))
                .andExpect(jsonPath("$.data.countryCode").value("BJ"));

        verify(mobileOnboardingJourneyService).selectCountry(actorUserId, accountHolderId, "BJ");
    }

    private MockMvc mockMvcForController() {
        MobileOnboardingController controller = new MobileOnboardingController(
                mobileOnboardingService,
                mobileOnboardingJourneyService,
                mobileOnboardingStatusService,
                managedAccountReadContextService
        );
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void setAuthenticatedPrincipal(UUID actorUserId, UUID accountHolderId) {
        AuthenticatedPrincipal principal = AuthenticatedPrincipal.builder()
                .userId(actorUserId)
                .accountHolderId(accountHolderId)
                .firebaseUid("firebase-user")
                .email("member@ccapp.test")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );
    }
}
