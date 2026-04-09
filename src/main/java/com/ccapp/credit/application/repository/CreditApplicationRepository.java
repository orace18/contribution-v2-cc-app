package com.ccapp.credit.application.repository;

import com.ccapp.credit.application.domain.entity.CreditApplication;
import com.ccapp.credit.application.domain.enums.CreditApplicationProductType;
import com.ccapp.credit.application.domain.enums.CreditApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditApplicationRepository extends JpaRepository<CreditApplication, UUID> {

    List<CreditApplication> findByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId);

    Optional<CreditApplication> findByIdAndAccountHolderId(UUID id, UUID accountHolderId);

    boolean existsByAccountHolderIdAndCircleIdAndProductTypeAndWorkflowStateIn(
            UUID accountHolderId,
            UUID circleId,
            CreditApplicationProductType productType,
            Collection<CreditApplicationStatus> workflowStates
    );
}
