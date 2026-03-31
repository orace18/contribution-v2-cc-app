package com.ccapp.identity.verification.repository;

import com.ccapp.identity.verification.domain.entity.PhoneVerification;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, UUID> {

    Optional<PhoneVerification> findByIdAndRequesterAccountHolderId(UUID id, UUID requesterAccountHolderId);

    long countByRequesterAccountHolderIdAndCreatedAtAfter(UUID requesterAccountHolderId, OffsetDateTime after);

    Optional<PhoneVerification> findTopByRequesterAccountHolderIdAndPhoneNumberAndStatusOrderByCreatedAtDesc(
            UUID requesterAccountHolderId,
            String phoneNumber,
            PhoneVerificationStatus status
    );

    Optional<PhoneVerification> findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
            UUID requesterAccountHolderId,
            PhoneVerificationStatus status
    );

    boolean existsByRequesterAccountHolderIdAndPhoneNumberAndStatus(UUID requesterAccountHolderId,
                                                                    String phoneNumber,
                                                                    PhoneVerificationStatus status);
}
