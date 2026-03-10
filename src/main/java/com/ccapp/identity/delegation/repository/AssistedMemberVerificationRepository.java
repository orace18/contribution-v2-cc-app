package com.ccapp.identity.delegation.repository;

import com.ccapp.identity.delegation.domain.entity.AssistedMemberVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssistedMemberVerificationRepository extends JpaRepository<AssistedMemberVerification, UUID> {

    List<AssistedMemberVerification> findByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId);
}
