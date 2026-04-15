package com.ccapp.kyc.repository;

import com.ccapp.kyc.domain.IdentityVerificationEntity;
import com.ccapp.kyc.domain.VerificationDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocumentEntity, UUID> {

    Optional<VerificationDocumentEntity> findByVerification(IdentityVerificationEntity verification);
}
