package com.ccapp.mobile.onboarding.repository;

import com.ccapp.mobile.onboarding.domain.entity.MobilePlanSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MobilePlanSelectionRepository extends JpaRepository<MobilePlanSelection, UUID> {

    Optional<MobilePlanSelection> findByAccountHolderId(UUID accountHolderId);
}
