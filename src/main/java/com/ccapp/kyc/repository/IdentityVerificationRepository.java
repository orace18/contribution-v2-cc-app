package com.ccapp.kyc.repository;

import com.ccapp.kyc.domain.IdentityVerificationEntity;
import com.ccapp.kyc.domain.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerificationEntity, UUID> {

    Optional<IdentityVerificationEntity> findByIdAndAccountHolderId(UUID id, UUID accountHolderId);

    List<IdentityVerificationEntity> findAllByOrderByCreatedAtDesc();

    List<IdentityVerificationEntity> findByStatusOrderByCreatedAtDesc(VerificationStatus status);
}
