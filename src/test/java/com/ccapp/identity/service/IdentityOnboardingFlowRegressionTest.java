package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.BusinessExperienceLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.dto.OnboardingStatusResponse;
import com.ccapp.identity.dto.OnboardingStep1Request;
import com.ccapp.identity.dto.OnboardingStep2Request;
import com.ccapp.identity.dto.OnboardingStep3Request;
import com.ccapp.identity.mapper.IdentityMapper;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityOnboardingFlowRegressionTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private OnboardingCatalogService onboardingCatalogService;
    @Mock
    private IdentityMapper identityMapper;

    @InjectMocks
    private IdentityOnboardingService identityOnboardingService;

    @Test
    void onboardingStep2_shouldFail_whenStep1IsIncomplete() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId);
        UserProfile incompleteProfile = UserProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .firstName("John")
                .lastName(null)
                .phone("+229000000")
                .onboardingStep(OnboardingStep.STEP_1)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(incompleteProfile));

        OnboardingStep2Request request = new OnboardingStep2Request(
                "COMMERCE",
                BusinessExperienceLevel.BEGINNER,
                "BETWEEN_100K_300K",
                "Project description with enough details"
        );

        assertThrows(BusinessException.class, () -> identityOnboardingService.onboardingStep2(userId, request));
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void onboardingStep3_shouldCompleteOnboarding_whenAllPrerequisitesAreSatisfied() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId);
        UserProfile profile = UserProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .firstName("John")
                .lastName("Doe")
                .phone("+229000000")
                .activitySectorCode("COMMERCE")
                .businessExperience(BusinessExperienceLevel.ONE_TO_TWO_YEARS)
                .monthlyIncomeBracketCode("BETWEEN_100K_300K")
                .projectDescription("Detailed business project description")
                .onboardingStep(OnboardingStep.STEP_3)
                .onboardingCompleted(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingStatusResponse response = identityOnboardingService.onboardingStep3(
                userId,
                new OnboardingStep3Request(new BigDecimal("150000"), true, true)
        );

        assertTrue(response.step3Completed());
        assertTrue(response.onboardingCompleted());
        assertEquals(OnboardingStep.COMPLETED, response.onboardingStep());
        assertEquals(new BigDecimal("100.00"), response.progressPercentage());

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        UserProfile saved = profileCaptor.getValue();
        assertTrue(saved.isSolidarityGuaranteeAccepted());
        assertTrue(saved.isTermsAccepted());
        assertEquals(OnboardingStep.COMPLETED, saved.getOnboardingStep());
    }

    @Test
    void onboardingStep3_shouldFail_whenTermsAreNotAccepted() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId);
        UserProfile profile = UserProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .firstName("John")
                .lastName("Doe")
                .phone("+229000000")
                .activitySectorCode("COMMERCE")
                .businessExperience(BusinessExperienceLevel.ONE_TO_TWO_YEARS)
                .monthlyIncomeBracketCode("BETWEEN_100K_300K")
                .projectDescription("Detailed business project description")
                .onboardingStep(OnboardingStep.STEP_3)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        assertThrows(
                BusinessException.class,
                () -> identityOnboardingService.onboardingStep3(userId, new OnboardingStep3Request(new BigDecimal("50000"), true, false))
        );
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void onboardingStep1_shouldKeepKycNotStartedUntilDocumentsAreActuallySubmitted() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId);
        AccountHolder accountHolder = AccountHolder.builder()
                .id(userId)
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+229000000")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .kycStatus(KycStatus.NOT_STARTED)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(accountHolderService.updateProfile(
                eq(userId),
                eq("John"),
                eq("Doe"),
                eq("+229000000"),
                eq("BJ")
        )).thenReturn(accountHolder);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingStatusResponse response = identityOnboardingService.onboardingStep1(
                userId,
                new OnboardingStep1Request("John", "Doe", "BJ", "+229000000")
        );

        assertEquals(KycStatus.NOT_STARTED, user.getKycStatus());
        assertTrue(response.step1Completed());
        verify(userRepository, never()).save(any(User.class));
    }

    private User activeUser(UUID userId) {
        return User.builder()
                .id(userId)
                .firebaseUid("firebase-" + userId)
                .email("user@test.com")
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build();
    }
}
