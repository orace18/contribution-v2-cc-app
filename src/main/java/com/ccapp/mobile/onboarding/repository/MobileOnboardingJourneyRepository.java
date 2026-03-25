package com.ccapp.mobile.onboarding.repository;

import com.ccapp.mobile.onboarding.domain.entity.MobileOnboardingJourney;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MobileOnboardingJourneyRepository extends JpaRepository<MobileOnboardingJourney, UUID> {

    Optional<MobileOnboardingJourney> findByAccountHolderId(UUID accountHolderId);
}
