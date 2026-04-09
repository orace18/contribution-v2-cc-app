package com.ccapp.seedcapital.savings.repository;

import com.ccapp.seedcapital.savings.domain.entity.MemberSavingsContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberSavingsContributionRepository extends JpaRepository<MemberSavingsContribution, UUID> {

    List<MemberSavingsContribution> findByCycleIdOrderByCreatedAtAsc(UUID cycleId);

    Optional<MemberSavingsContribution> findByCycleIdAndAccountHolderId(UUID cycleId, UUID accountHolderId);
}
