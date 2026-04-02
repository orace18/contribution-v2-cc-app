package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.dto.OnboardingStatusResponse;
import com.ccapp.identity.dto.OnboardingStep1Request;
import com.ccapp.identity.dto.OnboardingStep2Request;
import com.ccapp.identity.dto.OnboardingStep3Request;
import com.ccapp.identity.mapper.IdentityMapper;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityOnboardingService {

    private static final int ONBOARDING_TOTAL_STEPS = 3;
    private static final String DEFAULT_COUNTRY_CODE = "INT";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final AccountHolderService accountHolderService;
    private final OnboardingCatalogService onboardingCatalogService;
    private final IdentityMapper identityMapper;

    @Transactional
    public OnboardingStatusResponse getOnboardingStatus(UUID userId) {
        User user = findUser(userId);
        ensureAccountActive(user);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .map(existing -> {
                    updateOnboardingState(existing);
                    return userProfileRepository.save(existing);
                })
                .orElse(null);
        return buildOnboardingStatus(user, profile);
    }

    @Transactional
    public void selectCountry(UUID userId, String countryCode) {
        User user = findUser(userId);
        var accountHolder = accountHolderService.selectCountry(user.getId(), countryCode.trim().toUpperCase(Locale.ROOT));
        UserProfile profile = getOrCreateProfile(user);
        profile.setCountry(accountHolder.getCountryCode());
        userProfileRepository.save(profile);
    }

    @Transactional
    public OnboardingStatusResponse onboardingStep1(UUID userId, OnboardingStep1Request request) {
        User user = findUser(userId);
        ensureAccountActive(user);

        AccountHolder accountHolder = accountHolderService.updateProfile(
                user.getId(),
                request.firstName().trim(),
                request.lastName().trim(),
                request.phone().trim(),
                request.country().trim().toUpperCase(Locale.ROOT)
        );
        String canonicalCountryCode = accountHolder.getCountryCode();

        UserProfile profile = getOrCreateProfile(user);
        profile.setFirstName(request.firstName().trim());
        profile.setLastName(request.lastName().trim());
        profile.setCountry(canonicalCountryCode);
        profile.setPhone(request.phone().trim());
        updateOnboardingState(profile);

        UserProfile saved = userProfileRepository.save(profile);
        if (user.getKycStatus() == KycStatus.NOT_STARTED) {
            user.setKycStatus(KycStatus.PENDING);
            userRepository.save(user);
        }

        return buildOnboardingStatus(user, saved);
    }

    @Transactional
    public OnboardingStatusResponse onboardingStep2(UUID userId, OnboardingStep2Request request) {
        User user = findUser(userId);
        ensureAccountActive(user);

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Onboarding step 1 must be completed first"));

        if (!isStep1Completed(profile)) {
            throw new BusinessException("Onboarding step 1 must be completed first");
        }

        profile.setActivitySectorCode(onboardingCatalogService.normalizeAndValidateActivitySectorCode(request.activitySectorCode()));
        profile.setBusinessExperience(request.businessExperience());
        profile.setMonthlyIncomeBracketCode(onboardingCatalogService.normalizeAndValidateIncomeBracketCode(request.monthlyIncomeBracketCode()));
        profile.setProjectDescription(request.projectDescription().trim());
        updateOnboardingState(profile);

        UserProfile saved = userProfileRepository.save(profile);
        return buildOnboardingStatus(user, saved);
    }

    @Transactional
    public OnboardingStatusResponse onboardingStep3(UUID userId, OnboardingStep3Request request) {
        User user = findUser(userId);
        ensureAccountActive(user);

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Onboarding step 1 must be completed first"));

        if (!isStep1Completed(profile)) {
            throw new BusinessException("Onboarding step 1 must be completed first");
        }
        if (!isStep2Completed(profile)) {
            throw new BusinessException("Onboarding step 2 must be completed first");
        }
        if (!Boolean.TRUE.equals(request.solidarityGuaranteeAccepted())) {
            throw new BusinessException("Solidarity guarantee acceptance is required");
        }
        if (!Boolean.TRUE.equals(request.termsAccepted())) {
            throw new BusinessException("Terms acceptance is required");
        }

        profile.setAvailableCapital(request.availableCapital());
        profile.setSolidarityGuaranteeAccepted(true);
        profile.setTermsAccepted(true);
        if (profile.getTermsAcceptedAt() == null) {
            profile.setTermsAcceptedAt(OffsetDateTime.now());
        }
        updateOnboardingState(profile);

        UserProfile saved = userProfileRepository.save(profile);
        return buildOnboardingStatus(user, saved);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserProfile getOrCreateProfile(User user) {
        return userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> UserProfile.builder()
                        .user(user)
                        .onboardingStep(OnboardingStep.STEP_1)
                        .onboardingCompleted(false)
                        .country(DEFAULT_COUNTRY_CODE)
                        .phone("")
                        .build());
    }

    private void ensureAccountActive(User user) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Account is not active");
        }
    }

    private void updateOnboardingState(UserProfile profile) {
        boolean step1Completed = isStep1Completed(profile);
        boolean step2Completed = step1Completed && isStep2Completed(profile);
        boolean step3Completed = step2Completed && isStep3Completed(profile);

        OnboardingStep onboardingStep = resolveOnboardingStep(step1Completed, step2Completed, step3Completed);
        profile.setOnboardingStep(onboardingStep);
        profile.setOnboardingCompleted(step3Completed);
        if (step3Completed) {
            if (profile.getOnboardingCompletedAt() == null) {
                profile.setOnboardingCompletedAt(OffsetDateTime.now());
            }
        } else {
            profile.setOnboardingCompletedAt(null);
        }
        if (!profile.isTermsAccepted()) {
            profile.setTermsAcceptedAt(null);
        }
    }

    private boolean isStep1Completed(UserProfile profile) {
        return hasText(profile.getFirstName())
                && hasText(profile.getLastName())
                && hasText(profile.getPhone());
    }

    private boolean isStep2Completed(UserProfile profile) {
        return hasText(profile.getActivitySectorCode())
                && profile.getBusinessExperience() != null
                && hasText(profile.getMonthlyIncomeBracketCode())
                && hasText(profile.getProjectDescription())
                && profile.getProjectDescription().trim().length() >= 20;
    }

    private boolean isStep3Completed(UserProfile profile) {
        return profile.getAvailableCapital() != null
                && profile.getAvailableCapital().compareTo(BigDecimal.ZERO) >= 0
                && profile.isSolidarityGuaranteeAccepted()
                && profile.isTermsAccepted();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private OnboardingStep resolveOnboardingStep(boolean step1Completed, boolean step2Completed, boolean step3Completed) {
        if (step3Completed) {
            return OnboardingStep.COMPLETED;
        }
        if (step2Completed) {
            return OnboardingStep.STEP_3;
        }
        if (step1Completed) {
            return OnboardingStep.STEP_2;
        }
        return OnboardingStep.STEP_1;
    }

    private OnboardingStatusResponse buildOnboardingStatus(User user, UserProfile profile) {
        boolean step1Completed = profile != null && isStep1Completed(profile);
        boolean step2Completed = profile != null && step1Completed && isStep2Completed(profile);
        boolean step3Completed = profile != null && step2Completed && isStep3Completed(profile);
        OnboardingStep onboardingStep = resolveOnboardingStep(step1Completed, step2Completed, step3Completed);
        BigDecimal progressPercentage = BigDecimal.valueOf(((step1Completed ? 1 : 0) + (step2Completed ? 1 : 0) + (step3Completed ? 1 : 0)) * 100L)
                .divide(BigDecimal.valueOf(ONBOARDING_TOTAL_STEPS), 2, RoundingMode.HALF_UP);
        int currentStep = step3Completed ? 3 : (step2Completed ? 3 : (step1Completed ? 2 : 1));

        return new OnboardingStatusResponse(
                user.getId(),
                onboardingStep,
                currentStep,
                ONBOARDING_TOTAL_STEPS,
                progressPercentage,
                step1Completed,
                step2Completed,
                step3Completed,
                step3Completed,
                profile != null ? profile.getOnboardingCompletedAt() : null,
                profile != null ? identityMapper.toDto(profile) : null
        );
    }
}
