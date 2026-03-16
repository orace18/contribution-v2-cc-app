package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.PartnerUser;
import com.ccapp.financing.domain.enums.PartnerUserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerUserRepository extends JpaRepository<PartnerUser, UUID> {

    Optional<PartnerUser> findByPartnerIdAndAccountHolderId(UUID partnerId, UUID accountHolderId);

    boolean existsByPartnerIdAndAccountHolderIdAndStatus(UUID partnerId, UUID accountHolderId, PartnerUserStatus status);

    List<PartnerUser> findByAccountHolderIdAndStatus(UUID accountHolderId, PartnerUserStatus status);
}
