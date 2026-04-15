package com.ccapp.kyc.repository;

import com.ccapp.kyc.domain.IdentityVerificationEntity;
import com.ccapp.kyc.domain.VerificationAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationAnalysisRepository extends JpaRepository<VerificationAnalysisEntity, UUID> {

    Optional<VerificationAnalysisEntity> findByVerification(IdentityVerificationEntity verification);
}
